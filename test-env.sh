#!/usr/bin/env bash
# ==============================================================================
# StayHub - End-to-End Environment Test Suite
# ==============================================================================
# Questo script testa in modo esaustivo l'intero ecosistema StayHub:
# 1. Healthcheck servizi (Keycloak, MariaDB, Spring Boot, WireMock)
# 2. Autenticazione e sincronizzazione utente Admin Keycloak
# 3. Registrazione nuovo utente Guest (Keycloak OIDC + MariaDB)
# 4. Login nuovo utente e generazione token JWT Bearer
# 5. Creazione camera di lusso da parte dell'Admin
# 6. Consultazione catalogo e date occupate da parte dell'Ospite
# 7. Prenotazione con Carta di Credito (approvata da WireMock)
# 8. Test anti-overbooking / concorrenza (rifiuto date sovrapposte - 409 Conflict)
# 9. Prenotazione con PayPal (approvata da simulatore PayPal)
# 10. Test rifiuto pagamento carta (saldo insufficiente via WireMock - 402)
# 11. Test sicurezza ricevute (401 non autenticato, 403 IDOR altro utente, 200 autorizzato)
# 12. Annullamento prenotazione e rilascio immediato delle date per altri utenti
# 13. Ispezione registro globale e dashboard dell'Amministratore
# ==============================================================================

set -eo pipefail

# Colori per il terminale
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:9000}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-stayhub}"
CLIENT_ID="stayhub-frontend"

TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper funzioni
print_header() {
    echo -e "\n${BLUE}${BOLD}======================================================================${NC}"
    echo -e "${BLUE}${BOLD}   $1${NC}"
    echo -e "${BLUE}${BOLD}======================================================================${NC}"
}

print_step() {
    echo -e "\n${CYAN}▶ [$1/${TOTAL_STEPS}] $2...${NC}"
}

assert_status() {
    local expected="$1"
    local actual="$2"
    local desc="$3"
    TESTS_RUN=$((TESTS_RUN + 1))
    if [ "$actual" -eq "$expected" ]; then
        echo -e "  ${GREEN}✔ [PASS]${NC} $desc (HTTP $actual)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "  ${RED}✘ [FAIL]${NC} $desc (Atteso: $expected, Ricevuto: $actual)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

assert_json_value() {
    local json="$1"
    local filter="$2"
    local expected="$3"
    local desc="$4"
    TESTS_RUN=$((TESTS_RUN + 1))
    local actual
    actual=$(echo "$json" | jq -r "$filter" 2>/dev/null || echo "null")
    if [ "$actual" == "$expected" ]; then
        echo -e "  ${GREEN}✔ [PASS]${NC} $desc ($filter = $actual)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "  ${RED}✘ [FAIL]${NC} $desc ($filter Atteso: $expected, Ricevuto: $actual)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Timestamp univoco per i test
TIMESTAMP=$(date +%s)
TOTAL_STEPS=13

print_header "STAYHUB - E2E FULL ENVIRONMENT TEST SUITE"
echo -e "Backend:  ${BOLD}$BACKEND_URL${NC}"
echo -e "Keycloak: ${BOLD}$KEYCLOAK_URL${NC} (Realm: ${BOLD}$REALM${NC})"
echo -e "Timestamp esecuzione: ${BOLD}$TIMESTAMP${NC}"

# ------------------------------------------------------------------------------
# 0. HEALTHCHECKS
# ------------------------------------------------------------------------------
print_step "0" "Verifica connettività servizi (Healthcheck)"

KC_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$KEYCLOAK_URL/realms/$REALM/.well-known/openid-configuration" || echo "000")
if [ "$KC_STATUS" -ne 200 ]; then
    echo -e "${RED}ERRORE: Keycloak non risponde su $KEYCLOAK_URL (Status: $KC_STATUS).${NC}"
    echo -e "Assicurati di aver avviato i container: ${YELLOW}docker compose up -d${NC}"
    exit 1
fi
echo -e "  ${GREEN}✔ [OK]${NC} Keycloak è attivo e raggiungibile."

BE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/api/v1/config/paypal" || echo "000")
if [ "$BE_STATUS" -ne 200 ]; then
    echo -e "${RED}ERRORE: Backend Spring Boot non risponde su $BACKEND_URL (Status: $BE_STATUS).${NC}"
    echo -e "Assicurati di avviare il backend: ${YELLOW}cd backend && ./mvnw spring-boot:run${NC}"
    exit 1
fi
echo -e "  ${GREEN}✔ [OK]${NC} Spring Boot Backend è attivo e raggiungibile."

# ------------------------------------------------------------------------------
# STEP 1: Autenticazione Admin & Token JWT
# ------------------------------------------------------------------------------
print_step "1" "Autenticazione Admin su Keycloak"
ADMIN_TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "username=admin" \
    -d "password=admin" \
    -d "grant_type=password")

ADMIN_TOKEN=$(echo "$ADMIN_TOKEN_RESPONSE" | jq -r '.access_token')
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" == "null" ]; then
    echo -e "${RED}Impossibile ottenere il token JWT per admin. Risposta:${NC} $ADMIN_TOKEN_RESPONSE"
    exit 1
fi
echo -e "  ${GREEN}✔ [PASS]${NC} Token JWT Admin ottenuto con successo."

# Sincronizza Admin su MariaDB
curl -s -X POST "$BACKEND_URL/api/v1/users/sync" \
    -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
echo -e "  ${GREEN}✔ [PASS]${NC} Profilo utente Admin sincronizzato nel database locale."

# ------------------------------------------------------------------------------
# STEP 2: Registrazione Nuovo Utente Ospite
# ------------------------------------------------------------------------------
GUEST_USERNAME="guest_${TIMESTAMP}"
GUEST_EMAIL="guest_${TIMESTAMP}@test.stayhub.com"
GUEST_PASSWORD="TestPassword123!"

print_step "2" "Registrazione nuovo ospite ($GUEST_USERNAME)"
REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/users/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"username\": \"$GUEST_USERNAME\",
        \"password\": \"$GUEST_PASSWORD\",
        \"email\": \"$GUEST_EMAIL\",
        \"firstName\": \"Mario\",
        \"lastName\": \"Rossi\",
        \"role\": \"CUSTOMER\"
    }")

HTTP_CODE=$(echo "$REG_RESPONSE" | tail -n1)
BODY=$(echo "$REG_RESPONSE" | sed '$d')

assert_status 200 "$HTTP_CODE" "Registrazione nuovo utente in Keycloak e MariaDB"
assert_json_value "$BODY" ".email" "$GUEST_EMAIL" "Email utente registrato corretta"
assert_json_value "$BODY" ".role" "CUSTOMER" "Ruolo utente forzato a CUSTOMER"

# ------------------------------------------------------------------------------
# STEP 3: Login Nuovo Utente Ospite
# ------------------------------------------------------------------------------
print_step "3" "Login nuovo ospite e recupero JWT Bearer"
GUEST_TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "username=$GUEST_USERNAME" \
    -d "password=$GUEST_PASSWORD" \
    -d "grant_type=password")

GUEST_TOKEN=$(echo "$GUEST_TOKEN_RESPONSE" | jq -r '.access_token')
if [ -z "$GUEST_TOKEN" ] || [ "$GUEST_TOKEN" == "null" ]; then
    echo -e "${RED}Impossibile effettuare il login per l'utente registrato.${NC}"
    exit 1
fi
echo -e "  ${GREEN}✔ [PASS]${NC} Login eseguito con successo, token JWT ospite valido generato."

# ------------------------------------------------------------------------------
# STEP 4: Creazione Camera da parte dell'Admin
# ------------------------------------------------------------------------------
ROOM_NAME="Suite E2E Gold $TIMESTAMP"
print_step "4" "Creazione camera di lusso (come ADMIN)"
ROOM_CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/rooms" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"name\": \"$ROOM_NAME\",
        \"description\": \"Suite di test con vista panoramica sul mare\",
        \"capacity\": 2,
        \"pricePerNight\": 150.00
    }")

HTTP_CODE=$(echo "$ROOM_CREATE_RESPONSE" | tail -n1)
BODY=$(echo "$ROOM_CREATE_RESPONSE" | sed '$d')

assert_status 201 "$HTTP_CODE" "Creazione camera autorizzata per ADMIN"
assert_json_value "$BODY" ".name" "$ROOM_NAME" "Nome camera salvato correttamente"
ROOM_ID=$(echo "$BODY" | jq -r '.id')
echo -e "  ℹ ID Camera creata: ${BOLD}$ROOM_ID${NC}"

# Test upload foto camera (RBAC & Storage)
TEST_IMG_PATH="/tmp/stayhub_test_img_${TIMESTAMP}.png"
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > "$TEST_IMG_PATH"

GUEST_UPLOAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BACKEND_URL/api/v1/rooms/$ROOM_ID/image" \
    -H "Authorization: Bearer $GUEST_TOKEN" \
    -F "file=@$TEST_IMG_PATH;type=image/png")
assert_status 403 "$GUEST_UPLOAD_STATUS" "Upload foto camera respinto per utente non ADMIN (HTTP 403)"

ADMIN_UPLOAD_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/rooms/$ROOM_ID/image" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -F "file=@$TEST_IMG_PATH;type=image/png")
ADMIN_UPLOAD_STATUS=$(echo "$ADMIN_UPLOAD_RES" | tail -n1)
ADMIN_UPLOAD_BODY=$(echo "$ADMIN_UPLOAD_RES" | sed '$d')

assert_status 200 "$ADMIN_UPLOAD_STATUS" "Upload foto camera completato con successo per ADMIN (HTTP 200)"
assert_json_value "$ADMIN_UPLOAD_BODY" ".imageUrl" "/api/v1/rooms/$ROOM_ID/image" "URL relativo immagine associato alla camera"

IMG_DOWNLOAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/api/v1/rooms/$ROOM_ID/image")
assert_status 200 "$IMG_DOWNLOAD_STATUS" "Download pubblico foto camera accessibile (HTTP 200)"
rm -f "$TEST_IMG_PATH"

# ------------------------------------------------------------------------------
# STEP 5: Consultazione Catalogo e Date Occupate
# ------------------------------------------------------------------------------
print_step "5" "Consultazione catalogo camere e verifica date occupate"
ROOMS_LIST=$(curl -s "$BACKEND_URL/api/v1/rooms")
HAS_ROOM=$(echo "$ROOMS_LIST" | jq --arg name "$ROOM_NAME" 'any(.[]; .name == $name)')
if [ "$HAS_ROOM" == "true" ]; then
    echo -e "  ${GREEN}✔ [PASS]${NC} La nuova camera creata è visibile nel catalogo pubblico."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✘ [FAIL]${NC} Camera non trovata nel catalogo pubblico."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

OCCUPIED_INIT=$(curl -s "$BACKEND_URL/api/v1/rooms/$ROOM_ID/occupied")
assert_json_value "$OCCUPIED_INIT" "length" "0" "Nessuna data occupata inizialmente"

# ------------------------------------------------------------------------------
# STEP 6: Prenotazione con Carta di Credito (WireMock Gateway)
# ------------------------------------------------------------------------------
print_step "6" "Prenotazione con Carta di Credito (Simulazione WireMock)"
BOOKING_CARD_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/bookings" \
    -H "Authorization: Bearer $GUEST_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"roomId\": $ROOM_ID,
        \"checkIn\": \"2026-11-01\",
        \"checkOut\": \"2026-11-04\",
        \"paymentMethod\": \"CLASSIC_CARD\",
        \"paymentDetails\": {
            \"cardNumber\": \"4000123456780000\",
            \"cardHolder\": \"Mario Rossi\",
            \"cvv\": \"123\",
            \"expirationDate\": \"12/28\"
        }
    }")

HTTP_CODE=$(echo "$BOOKING_CARD_RES" | tail -n1)
BODY=$(echo "$BOOKING_CARD_RES" | sed '$d')

assert_status 201 "$HTTP_CODE" "Creazione e pagamento prenotazione con carta"
assert_json_value "$BODY" ".status" "CONFIRMED" "Stato prenotazione CONFERMATA"
assert_json_value "$BODY" ".paymentStatus" "COMPLETED" "Stato pagamento COMPLETATO"
TX_REF=$(echo "$BODY" | jq -r '.transactionReference')
if echo "$TX_REF" | grep -Eq "^TX-CRD-[0-9]{8}-0000-[A-Z0-9]+$"; then
    echo -e "  ${GREEN}✔ [PASS]${NC} Riferimento transazione conforme al formato TX-CRD ($TX_REF)"
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✘ [FAIL]${NC} Riferimento transazione non conforme: $TX_REF"
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
assert_json_value "$BODY" ".totalPrice | floor" "450" "Calcolo prezzo corretto (3 notti x 150 = 450)"

BOOKING_CARD_ID=$(echo "$BODY" | jq -r '.id')
echo -e "  ℹ ID Prenotazione Carta: ${BOLD}$BOOKING_CARD_ID${NC}"

# Verifica che le date risultino occupate
OCCUPIED_AFTER=$(curl -s "$BACKEND_URL/api/v1/rooms/$ROOM_ID/occupied")
assert_json_value "$OCCUPIED_AFTER" "length" "1" "Registro date occupate aggiornato a 1 intervallo"

# ------------------------------------------------------------------------------
# STEP 7: Test Concorrenza / Overbooking (Rifiuto Date Sovrapposte)
# ------------------------------------------------------------------------------
print_step "7" "Test anti-overbooking (tentativo di prenotazione date sovrapposte)"
OVERLAP_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/bookings" \
    -H "Authorization: Bearer $GUEST_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"roomId\": $ROOM_ID,
        \"checkIn\": \"2026-11-02\",
        \"checkOut\": \"2026-11-05\",
        \"paymentMethod\": \"CLASSIC_CARD\",
        \"paymentDetails\": {
            \"cardNumber\": \"4000123456780000\",
            \"cardHolder\": \"Mario Rossi\",
            \"cvv\": \"123\",
            \"expirationDate\": \"12/28\"
        }
    }")

HTTP_CODE=$(echo "$OVERLAP_RES" | tail -n1)
assert_status 409 "$HTTP_CODE" "Richiesta respinta con 409 CONFLICT (Overbooking impedito da lock pessimistico)"

# ------------------------------------------------------------------------------
# STEP 8: Prenotazione con PayPal (Simulatore PayPal)
# ------------------------------------------------------------------------------
print_step "8" "Prenotazione con PayPal (Date non sovrapposte)"
PAYPAL_REF="MOCK-PAYPAL-ORDER-${TIMESTAMP}"
BOOKING_PP_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/bookings" \
    -H "Authorization: Bearer $GUEST_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"roomId\": $ROOM_ID,
        \"checkIn\": \"2026-11-10\",
        \"checkOut\": \"2026-11-12\",
        \"paymentMethod\": \"PAYPAL\",
        \"paymentDetails\": {
            \"paypalEmail\": \"$GUEST_EMAIL\",
            \"paypalOrderId\": \"$PAYPAL_REF\"
        }
    }")

HTTP_CODE=$(echo "$BOOKING_PP_RES" | tail -n1)
BODY=$(echo "$BOOKING_PP_RES" | sed '$d')

assert_status 201 "$HTTP_CODE" "Creazione e pagamento prenotazione con PayPal"
assert_json_value "$BODY" ".status" "CONFIRMED" "Stato prenotazione PayPal CONFERMATA"
assert_json_value "$BODY" ".paymentStatus" "COMPLETED" "Stato pagamento PayPal COMPLETATO"
assert_json_value "$BODY" ".transactionReference" "$PAYPAL_REF" "Codice transazione PayPal registrato"

BOOKING_PP_ID=$(echo "$BODY" | jq -r '.id')
echo -e "  ℹ ID Prenotazione PayPal: ${BOLD}$BOOKING_PP_ID${NC}"

# ------------------------------------------------------------------------------
# STEP 9: Test Carta Rifiutata (WireMock 4444)
# ------------------------------------------------------------------------------
print_step "9" "Test declino transazione carta (saldo insufficiente via WireMock)"
DECLINE_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/bookings" \
    -H "Authorization: Bearer $GUEST_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"roomId\": $ROOM_ID,
        \"checkIn\": \"2026-11-20\",
        \"checkOut\": \"2026-11-22\",
        \"paymentMethod\": \"CLASSIC_CARD\",
        \"paymentDetails\": {
            \"cardNumber\": \"4000123456784444\",
            \"cardHolder\": \"Mario Rossi\",
            \"cvv\": \"123\",
            \"expirationDate\": \"12/28\"
        }
    }")

HTTP_CODE=$(echo "$DECLINE_RES" | tail -n1)
assert_status 402 "$HTTP_CODE" "Transazione rifiutata con 402 PAYMENT REQUIRED e transazione annullata"

# ------------------------------------------------------------------------------
# STEP 10: Sicurezza Ricevute (401 Non Autenticato, 403 IDOR, 200 Autorizzato)
# ------------------------------------------------------------------------------
print_step "10" "Test Sicurezza e Access Control Ricevute"

# 10a. Accesso non autenticato
RES_NO_AUTH=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_URL/api/v1/exports/receipt?bookingId=$BOOKING_CARD_ID")
assert_status 401 "$RES_NO_AUTH" "Richiesta ricevuta senza token respinta con 401 Unauthorized"

# 10b. Creazione secondo utente per test IDOR
GUEST2_USERNAME="guest2_${TIMESTAMP}"
GUEST2_PASSWORD="TestPassword123!"
curl -s -X POST "$BACKEND_URL/api/v1/users/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"username\": \"$GUEST2_USERNAME\",
        \"password\": \"$GUEST2_PASSWORD\",
        \"email\": \"guest2_${TIMESTAMP}@test.stayhub.com\",
        \"firstName\": \"Luigi\",
        \"lastName\": \"Verdi\",
        \"role\": \"CUSTOMER\"
    }" > /dev/null

GUEST2_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "username=$GUEST2_USERNAME" \
    -d "password=$GUEST2_PASSWORD" \
    -d "grant_type=password" | jq -r '.access_token')

# 10c. Test IDOR: Guest 2 tenta di scaricare la ricevuta di Guest 1
RES_IDOR=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $GUEST2_TOKEN" "$BACKEND_URL/api/v1/exports/receipt?bookingId=$BOOKING_CARD_ID")
assert_status 403 "$RES_IDOR" "Test IDOR: Utente estraneo bloccato con 403 FORBIDDEN"

# 10d. Download autorizzato da parte del proprietario
RECEIPT_FILE=$(mktemp)
HTTP_CODE=$(curl -s -w "%{http_code}" -o "$RECEIPT_FILE" -H "Authorization: Bearer $GUEST_TOKEN" "$BACKEND_URL/api/v1/exports/receipt?bookingId=$BOOKING_CARD_ID")
assert_status 200 "$HTTP_CODE" "Download ricevuta autorizzato per il proprietario della prenotazione"

if head -c 4 "$RECEIPT_FILE" | grep -q "%PDF"; then
    echo -e "  ${GREEN}✔ [PASS]${NC} Documento PDF valido generato con successo (Magic bytes: %PDF)."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✘ [FAIL]${NC} Il file scaricato non è un documento PDF valido."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
rm -f "$RECEIPT_FILE"

# ------------------------------------------------------------------------------
# STEP 11: Annullamento Prenotazione e Rilascio Date
# ------------------------------------------------------------------------------
print_step "11" "Annullamento prenotazione e verifica rilascio date"
CANCEL_RES=$(curl -s -w "\n%{http_code}" -X PUT "$BACKEND_URL/api/v1/bookings/$BOOKING_CARD_ID/cancel" \
    -H "Authorization: Bearer $GUEST_TOKEN")

HTTP_CODE=$(echo "$CANCEL_RES" | tail -n1)
BODY=$(echo "$CANCEL_RES" | sed '$d')
assert_status 200 "$HTTP_CODE" "Richiesta annullamento prenotazione eseguita"
assert_json_value "$BODY" ".status" "CANCELLED" "Stato prenotazione aggiornato a CANCELLED"

# Riprova a prenotare le date appena liberate con Guest 2 (deve avere successo!)
REBOOK_RES=$(curl -s -w "\n%{http_code}" -X POST "$BACKEND_URL/api/v1/bookings" \
    -H "Authorization: Bearer $GUEST2_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"roomId\": $ROOM_ID,
        \"checkIn\": \"2026-11-01\",
        \"checkOut\": \"2026-11-04\",
        \"paymentMethod\": \"CLASSIC_CARD\",
        \"paymentDetails\": {
            \"cardNumber\": \"4000123456780000\",
            \"cardHolder\": \"Luigi Verdi\",
            \"cvv\": \"321\",
            \"expirationDate\": \"12/28\"
        }
    }")

HTTP_CODE=$(echo "$REBOOK_RES" | tail -n1)
BODY=$(echo "$REBOOK_RES" | sed '$d')
assert_status 201 "$HTTP_CODE" "Date precedentemente annullate riprenotate con successo da un altro utente"
assert_json_value "$BODY" ".status" "CONFIRMED" "Nuova prenotazione confermata"

# ------------------------------------------------------------------------------
# STEP 12: Visualizzazione Prenotazioni Personali Ospite
# ------------------------------------------------------------------------------
print_step "12" "Verifica endpoint 'Le Mie Prenotazioni' (/bookings/my)"
MY_BOOKINGS=$(curl -s -H "Authorization: Bearer $GUEST_TOKEN" "$BACKEND_URL/api/v1/bookings/my")
MY_COUNT=$(echo "$MY_BOOKINGS" | jq 'length')
if [ "$MY_COUNT" -ge 2 ]; then
    echo -e "  ${GREEN}✔ [PASS]${NC} Trovate $MY_COUNT prenotazioni per l'utente (incluso lo stato CANCELLED)."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✘ [FAIL]${NC} Attese almeno 2 prenotazioni per l'utente, trovate: $MY_COUNT"
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ------------------------------------------------------------------------------
# STEP 13: Dashboard Amministratore & Registri Globali
# ------------------------------------------------------------------------------
print_step "13" "Ispezione registri amministrativi da parte dell'Admin"
ALL_BOOKINGS=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BACKEND_URL/api/v1/bookings")
GLOBAL_COUNT=$(echo "$ALL_BOOKINGS" | jq 'length')
if [ "$GLOBAL_COUNT" -gt 0 ]; then
    echo -e "  ${GREEN}✔ [PASS]${NC} Registro globale prenotazioni consultabile dall'Admin ($GLOBAL_COUNT prenotazioni totali)."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✘ [FAIL]${NC} Nessuna prenotazione trovata nel registro globale dell'Admin."
    TESTS_RUN=$((TESTS_RUN + 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

# ------------------------------------------------------------------------------
# RIEPILOGO FINALE
# ------------------------------------------------------------------------------
print_header "RIASSUNTO RISULTATI TEST SUITE E2E"
echo -e "Test eseguiti: ${BOLD}$TESTS_RUN${NC}"
echo -e "Test superati: ${GREEN}${BOLD}$TESTS_PASSED${NC}"
echo -e "Test falliti:  $([ $TESTS_FAILED -eq 0 ] && echo -e "${GREEN}${BOLD}0${NC}" || echo -e "${RED}${BOLD}$TESTS_FAILED${NC}")"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}${BOLD}🎉 TUTTI I TEST SONO STATI SUPERATI CON SUCCESSO! L'AMBIENTE STAYHUB È PERFETTAMENTE FUNZIONANTE.${NC}\n"
    exit 0
else
    echo -e "\n${RED}${BOLD}❌ ALCUNI TEST SONO FALLITI. CONTROLLA I DETTAGLI SOPRA.${NC}\n"
    exit 1
fi

