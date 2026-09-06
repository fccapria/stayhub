# StayHub

> Modern full-stack accommodation and B&B booking platform built with Spring Boot, Angular, Keycloak, and MariaDB.

---

## 🛠️ Tech Stack

- **Backend:** Java 21, Spring Boot 4, Spring Security (OAuth2 / OIDC Keycloak), Spring Data JPA, OpenPDF, Jakarta Validation
- **Frontend:** Angular 19+ SPA (SSR Ready), TypeScript, CSS3, Keycloak JS
- **Auth & IAM:** Keycloak (OIDC Realm with roles: `CUSTOMER`, `HOST`, `ADMIN`)
- **Database & Infrastructure:** MariaDB, Docker Compose

---

## 🚀 Quick Start & Operating Guide

## 1. Local Infrastructure Services (Docker)

Docker Compose hosts the relational database (MariaDB) and the identity provider (Keycloak).

* **Start Services**:
  ```bash
  docker compose up -d
  ```
* **Stop Services**:
  ```bash
  docker compose down
  ```
* **Verify running containers**:
  ```bash
  docker ps
  ```
  * **Keycloak**: Listening on `http://localhost:9000`
  * **MariaDB**: Listening on `http://localhost:3306`

---

## 2. Running the Backend (Spring Boot)

The REST API backend runs on port `8080`.

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Start the application using Java 21:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Nota: assicurati che sia configurato JDK 21+ nel tuo ambiente o esporta `JAVA_HOME`)*

---

## 3. Running the Frontend (Angular SPA)

The Angular client runs on port `4200`.

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Start the local server:
   ```bash
   npm start
   ```
3. Open the browser and visit: **`http://localhost:4200`**

> [!IMPORTANT]
> Always access the application via `http://localhost:4200`. Do not use `127.0.0.1` or other host names, as Keycloak is configured to allow redirects strictly to `http://localhost:4200`.

---

## 4. Resetting Database Records (Rooms & Bookings)

If you want to clear the database catalog and start over, resetting all reservation history, payments, rooms, and registered users back to clean states (resetting Auto-Increment IDs to `1`):

Run this single command in your terminal:

```bash
docker exec -i stayhub-mariadb mariadb -ustayhub_user -pstayhub_password -e "SET FOREIGN_KEY_CHECKS = 0; TRUNCATE TABLE stayhub.payments; TRUNCATE TABLE stayhub.bookings; TRUNCATE TABLE stayhub.rooms; TRUNCATE TABLE stayhub.users; SET FOREIGN_KEY_CHECKS = 1;"
```

---

## 5. Creating New Users (Keycloak OIDC)

User authentication and roles are managed by Keycloak. Local user rows are synchronized automatically when the user signs in to the Angular app for the first time.

### Step-by-Step User Creation:

1. Open the Keycloak Administration Console: **`http://localhost:9000`**
2. Click **Administration Console** and log in:
   * **Username**: `admin`
   * **Password**: `admin`
3. Select the **`stayhub`** realm from the dropdown menu in the top-left corner.
4. Select **Users** under the *Manage* tab in the left sidebar.
5. Click **Add user** (top-right).
6. Fill in the user profile:
   * **Username**: Choose a unique name (e.g., `alice_guest`).
   * **Email / First Name / Last Name**: Fill in these details (they will sync to the B&B invoices).
   * Click **Create**.
7. Set the password:
   * Click the **Credentials** tab on the new user page.
   * Click **Set password**.
   * Enter the password.
   * Toggle **Temporary** to **OFF** (so they don't have to change it at their first login).
   * Click **Save** (and confirm by clicking *Set password*).
8. Map Roles (Permissions):
   * Click the **Role mapping** tab on the new user page.
   * Click **Assign role**.
   * Select the appropriate role:
     * **`CUSTOMER`**: Accesso come viaggiatore (esplorazione, prenotazione soggiorni, consultazione viaggi propri e download ricevute).
     * **`HOST`**: Locatore (gestione dei propri alloggi, consultazione delle prenotazioni e ricavi generati dai propri alloggi nel "Pannello Locatore", oltre a tutte le funzioni viaggiatore).
     * **`ADMIN`**: Amministratore di sistema (gestione catalogo globale, consultazione registri completi e supervisione).
   * Click **Assign**.

> [!NOTE]
> Utenti demo pre-configurati in Keycloak:
> - **`admin`** / `admin` (Ruoli: `ADMIN`, email: `admin@stayhub.com`)
> - **`host`** / `host` (Ruoli: `CUSTOMER`, `HOST`, email: `host@stayhub.com`)
> - **`customer`** / `customer` (Ruolo: `CUSTOMER`, email: `customer@stayhub.com`)
>
> Inoltre, qualsiasi utente può registrarsi direttamente dal form web scegliendo il ruolo **Viaggiatore** o **Locatore**, oppure attivare la modalità Locatore in qualunque momento con il pulsante *"Diventa Locatore"* nella barra di navigazione.

Now the user is active! They can log in immediately on `http://localhost:4200` to start using the system.

---

## 🧪 Testing

* **Backend Test Suite (Unit & Integration)**:
  ```bash
  cd backend && ./mvnw test
  ```
* **Frontend Test Suite (Vitest)**:
  ```bash
  cd frontend && npm test
  ```
* **Production Build**:
  ```bash
  cd frontend && npm run build
  ```
* **End-to-End Test Suite**:
  ```bash
  ./test-env.sh
  ```
