# StayHub - Operating & Development Guide

This guide describes how to start the StayHub application stack, reset database records, and manage Keycloak users.

---

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
   JAVA_HOME=/Users/fra/.java/openjdk21/Contents/Home ./mvnw spring-boot:run
   ```

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
     * **`CUSTOMER`**: Access to room booking, personal bookings, and downloads.
     * **`ADMIN`**: Access to global booking log and B&B room management.
   * Click **Assign**.

Now the user is active! They can log in immediately on `http://localhost:4200` to start using the system.
