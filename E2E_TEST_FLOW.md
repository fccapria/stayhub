# StayHub - Flusso di Test End-to-End (E2E Test Flow)

> **Documento Tecnico:** Architettura del flusso di test d'integrazione e verifica live dei servizi.  
> **Script Associato:** [`test-env.sh`](test-env.sh) dalla root del progetto.

---

## 1. Diagramma di Sequenza del Flusso E2E

Il seguente diagramma illustra l'interazione tra tutti i componenti dell'ecosistema StayHub durante l'esecuzione automatizzata dei 29 test:

```mermaid
sequenceDiagram
    autonumber
    actor Script as Test Suite (test-env.sh)
    participant KC as Keycloak IAM (:9000)
    participant BE as Spring Boot REST API (:8080)
    participant DB as MariaDB (:3306)
    participant WM as WireMock Gateway (:8089)

    Note over Script,WM: FASE 0: HEALTHCHECK DEI SERVIZI
    Script->>KC: GET /realms/stayhub/.well-known/openid-configuration
    KC-->>Script: 200 OK (Keycloak attivo)
    Script->>BE: GET /api/v1/config/paypal
    BE-->>Script: 200 OK (Backend attivo)

    Note over Script,WM: FASE 1: IAM & GESTIONE UTENTI
    Script->>KC: POST /token (Password Grant - admin / admin)
    KC-->>Script: 200 OK (JWT Admin con ruolo ROLE_ADMIN)
    Script->>BE: POST /api/v1/users/sync (Bearer JWT Admin)
    BE->>DB: Sincronizzazione utente locale MariaDB
    DB-->>BE: Utente salvato
    BE-->>Script: 200 OK

    Script->>BE: POST /api/v1/users/register (Nuovo ospite)
    BE->>KC: POST /admin/realms/stayhub/users (Creazione account OIDC)
    BE->>KC: POST /users/{id}/role-mappings (Assegnazione forzata CUSTOMER)
    BE->>DB: INSERT into users (id, email, ruolo CUSTOMER)
    BE-->>Script: 200 OK (Utente registrato senza privilege escalation)

    Script->>KC: POST /token (Password Grant - nuovo ospite)
    KC-->>Script: 200 OK (JWT Ospite con ruolo ROLE_CUSTOMER)

    Note over Script,WM: FASE 2: CATALOGO & GESTIONE CAMERE
    Script->>BE: POST /api/v1/rooms (Creazione camera di lusso - Header Admin)
    BE->>DB: INSERT into rooms (nome, capacità, prezzo, owner_id)
    BE-->>Script: 201 Created (ID Camera generato)

    Script->>BE: GET /api/v1/rooms (Consultazione catalogo pubblico)
    BE->>DB: SELECT * from rooms
    BE-->>Script: 200 OK (Verifica presenza camera nel catalogo)

    Script->>BE: GET /api/v1/rooms/{id}/occupied (Verifica disponibilità iniziale)
    BE-->>Script: 200 OK (Lista date occupate vuota)

    Note over Script,WM: FASE 3: PRENOTAZIONE CON CARTA & STRATEGY PATTERN
    Script->>BE: POST /api/v1/bookings (Metodo CLASSIC_CARD, Carta terminante in 0000)
    Note over BE: Acquisizione Lock Pessimistico sulla Camera
    BE->>WM: POST /api/v1/payments/card (Payload importo e dati carta)
    WM-->>BE: 200 OK (Status: APPROVED, ref: TX-CARD-0000-OK)
    BE->>DB: INSERT into bookings (status: CONFIRMED) & payments (COMPLETED)
    BE-->>Script: 201 Created (Prenotazione confermata)

    Note over Script,WM: FASE 4: TEST CONCORRENZA & OVERBOOKING
    Script->>BE: POST /api/v1/bookings (Tentativo prenotazione sulle STESSE date)
    Note over BE: Verifica sovrapposizione con Lock Pessimistico
    BE-->>Script: 409 Conflict (Overbooking impedito a livello database)

    Note over Script,WM: FASE 5: PRENOTAZIONE CON PAYPAL
    Script->>BE: POST /api/v1/bookings (Metodo PAYPAL, OrderID simulato)
    Note over BE: Dispatch dinamico verso PayPalPaymentStrategy
    BE->>DB: INSERT into bookings (CONFIRMED) & payments (COMPLETED)
    BE-->>Script: 201 Created (Transazione PayPal registrata)

    Note over Script,WM: FASE 6: DECLINO PAGAMENTO & ROLLBACK TRANSAZIONE
    Script->>BE: POST /api/v1/bookings (Carta terminante in 4444 - saldo insufficiente)
    BE->>WM: POST /api/v1/payments/card
    WM-->>BE: 402 Declined (Insufficient funds)
    Note over BE: Rollback della transazione DB
    BE-->>Script: 402 Payment Required (Nessuna prenotazione orfana creata)

    Note over Script,WM: FASE 7: SICUREZZA RICEVUTE & PREVENZIONE IDOR
    Script->>BE: GET /api/v1/exports/receipt?bookingId={id} (SENZA token)
    BE-->>Script: 401 Unauthorized (Accesso anonimo bloccato)

    Script->>KC: Creazione e Login secondo ospite (Guest 2)
    Script->>BE: GET /api/v1/exports/receipt?bookingId={id_guest1} (Token Guest 2)
    Note over BE: Controllo Ownership: currentUserId != booking.user.id
    BE-->>Script: 403 Forbidden (Attacco IDOR neutralizzato)

    Script->>BE: GET /api/v1/exports/receipt?bookingId={id_guest1} (Token Guest 1)
    BE-->>Script: 200 OK (Download file ricevuta CSV/PDF autorizzato per il proprietario)

    Note over Script,WM: FASE 8: CANCELLAZIONE & RILASCIO DATE
    Script->>BE: PUT /api/v1/bookings/{id}/cancel (Annullamento soggiorno)
    BE->>DB: UPDATE bookings SET status = 'CANCELLED'
    BE-->>Script: 200 OK (Stato CANCELLED)

    Script->>BE: POST /api/v1/bookings (Guest 2 prenota le date appena liberate)
    BE-->>Script: 201 Created (Date rilasciate correttamente e riutilizzabili)

    Note over Script,WM: FASE 9: VERIFICA REGISTRI & DASHBOARD ADMIN
    Script->>BE: GET /api/v1/bookings/my (Elenco prenotazioni personali ospite)
    BE-->>Script: 200 OK (2 prenotazioni trovate con relativi stati)
    Script->>BE: GET /api/v1/bookings (Elenco globale amministratore)
    BE-->>Script: 200 OK (Tutti i log consultabili dall'Admin)
```

---

## 2. Dettaglio delle Fasi di Collaudo

| Fase | Descrizione | Verifica Architetturale / Requisito |
| :--- | :--- | :--- |
| **Fase 0** | Healthcheck | Disponibilità delle porte `9000` (Keycloak), `3306` (MariaDB) e `8080` (Spring Boot). |
| **Fase 1** | IAM & Registrazione | Flusso OIDC OAuth2, prevenzione privilege escalation (forzatura ruolo `CUSTOMER`). |
| **Fase 2** | Catalogo Camere | Autorizzazione RBAC con `@PreAuthorize("hasRole('ADMIN')")` e consultazione pubblica. |
| **Fase 3** | Pagamento Carta | Pattern **Strategy** ([`PaymentStrategy.java`](backend/src/main/java/com/stayhub/backend/payment/PaymentStrategy.java)) e stubbing WireMock (`TX-CARD-0000-OK`). |
| **Fase 4** | Lock Concorrenza | `@Lock(LockModeType.PESSIMISTIC_WRITE)` su [`BookingRepository.java`](backend/src/main/java/com/stayhub/backend/repository/BookingRepository.java) per evitare race condition (HTTP 409). |
| **Fase 5** | Pagamento PayPal | Simulazione checkout PayPal Sandbox con tracciamento `transactionReference`. |
| **Fase 6** | Gestione Errori Carta | Risposta HTTP `402 Payment Required` ed esecuzione del rollback `@Transactional`. |
| **Fase 7** | Sicurezza Ricevute | Eliminazione vulnerabilità IDOR: protezione con token JWT e verifica proprietà (401 / 403 / 200). |
| **Fase 8** | Ciclo di Vita Soggiorno | Transizione di stato a `CANCELLED` ed esclusione delle prenotazioni cancellate dal controllo overlap. |
| **Fase 9** | Dashboard Admin | Endpoint per metriche e log globali riservati agli amministratori. |

---

## 3. Come Lanciare i Test

Assicurati che i container e il backend siano in esecuzione:

```bash
# 1. Avvia l'infrastruttura locale
docker compose up -d

# 2. Avvia il backend Spring Boot
cd backend && ./mvnw spring-boot:run

# 3. In un altro terminale, esegui la suite:
./test-env.sh
```

Lo script è totalmente idempotente e impiega timestamp univoci per ogni ciclo, garantendo test puliti e ripetibili all'infinito.
