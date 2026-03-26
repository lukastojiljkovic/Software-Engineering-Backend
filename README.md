# Banka 2025 - Tim 2 | Backend

Spring Boot backend aplikacija za simulaciju bankarskog sistema. Projekat za predmet Softversko Inzenjerstvo na Racunarskom fakultetu, 2025/26.

## Tech Stack

- **Java 17** + **Spring Boot 4.0.4**
- **MySQL 8.0** (H2 za testove)
- **Spring Security** + JWT (HS256) autentifikacija
- **Spring Data JPA** / Hibernate
- **Swagger/OpenAPI** dokumentacija (`/swagger-ui.html`)
- **PDFBox** za generisanje potvrda placanja
- **Lombok**, **JaCoCo** (code coverage)
- **Docker** + Docker Compose

## Pokretanje

### Docker (preporuceno)

```bash
docker compose up --build -d
```

Pokrece 4 servisa:
| Servis | Port | Opis |
|--------|------|------|
| **backend** | 8080 | Spring Boot API |
| **db** | 3307 | MySQL 8.0 |
| **adminer** | 9001 | DB admin UI |
| **seed** | - | Automatski pokrece `seed.sql` |

### Lokalno

Potreban MySQL na portu 3307, baza `banka2`.

```bash
cd banka2_bek
mvn spring-boot:run
```

### Testovi

```bash
cd banka2_bek
mvn test
```

44 test fajla (unit + integracioni testovi).

## Arhitektura

Layered arhitektura sa 17 domenskih modula:

```
rs.raf.banka2_bek/
  account/       # Racuni klijenata (tekuci, devizni, poslovni)
  actuary/       # Aktuari i agenti (limiti za trgovanje)
  auth/          # Autentifikacija (JWT, login, registracija, reset lozinke)
  card/          # Bankovne kartice
  client/        # CRUD klijenata
  company/       # Pravna lica
  currency/      # Valute (EUR, USD, RSD, ...)
  employee/      # CRUD zaposlenih + aktivacija naloga
  exchange/      # Kursna lista (Fixer.io API)
  loan/          # Krediti i otplata
  notification/  # Email notifikacije (async, event-driven)
  order/         # Nalozi za trgovanje hartijama
  payment/       # Placanja + primaoci + PDF potvrde
  stock/         # Listing hartija od vrednosti
  transaction/   # Istorija transakcija
  transfer/      # Interni i medjuvalutni transferi
  transfers/     # Transfer kontroler i servisi
```

Svaki modul prati strukturu: `controller/ dto/ model/ repository/ service/`

## API Endpointi

### Autentifikacija

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/auth/register` | Registracija klijenta |
| POST | `/auth/login` | Login (vraca accessToken + refreshToken) |
| POST | `/auth/refresh` | Refresh access tokena |
| POST | `/auth/password_reset/request` | Zahtev za reset lozinke |
| POST | `/auth/password_reset/confirm` | Potvrda reseta |
| POST | `/auth-employee/activate` | Aktivacija naloga zaposlenog |

### Zaposleni (ADMIN/EMPLOYEE)

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/employees` | Kreiranje zaposlenog |
| GET | `/employees` | Lista sa filterima |
| GET | `/employees/{id}` | Detalji zaposlenog |
| PUT | `/employees/{id}` | Azuriranje |
| PATCH | `/employees/{id}/deactivate` | Deaktivacija |

### Klijenti (ADMIN/EMPLOYEE)

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/clients` | Kreiranje klijenta |
| GET | `/clients` | Lista klijenata |
| GET | `/clients/{id}` | Detalji klijenta |
| PUT | `/clients/{id}` | Azuriranje |

### Racuni

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/accounts` | Kreiranje racuna |
| GET | `/accounts/my` | Moji racuni |
| GET | `/accounts/all` | Svi racuni (ADMIN) |
| GET | `/accounts/client/{clientId}` | Racuni klijenta (ADMIN) |
| GET | `/accounts/{id}` | Detalji racuna |
| PATCH | `/accounts/{id}/name` | Promena naziva |
| PATCH | `/accounts/{id}/limits` | Promena limita |
| PATCH | `/accounts/{id}/status` | Promena statusa |
| POST | `/accounts/requests` | Zahtev za novi racun |
| GET | `/accounts/requests/my` | Moji zahtevi |
| GET | `/accounts/requests` | Svi zahtevi (ADMIN) |
| PATCH | `/accounts/requests/{id}/approve` | Odobravanje zahteva |
| PATCH | `/accounts/requests/{id}/reject` | Odbijanje zahteva |

### Placanja

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/payments` | Novo placanje |
| GET | `/payments` | Lista placanja |
| GET | `/payments/{id}` | Detalji placanja |
| GET | `/payments/{id}/receipt` | PDF potvrda |
| GET | `/payments/history` | Istorija placanja |
| POST | `/payments/verify` | OTP verifikacija |

### Primaoci placanja

| Metod | Endpoint | Opis |
|-------|----------|------|
| GET | `/payment-recipients` | Lista sacuvanih primalaca |
| POST | `/payment-recipients` | Dodavanje primaoca |
| PUT | `/payment-recipients/{id}` | Azuriranje |
| DELETE | `/payment-recipients/{id}` | Brisanje |

### Transferi

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/transfers/internal` | Interni transfer |
| POST | `/transfers/fx` | Medjuvalutni transfer |
| GET | `/transfers` | Lista transfera |
| GET | `/transfers/{id}` | Detalji transfera |

### Kartice

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/cards` | Kreiranje kartice |
| GET | `/cards` | Lista kartica |
| GET | `/cards/account/{accountId}` | Kartice racuna |
| PATCH | `/cards/{id}/block` | Blokiranje |
| PATCH | `/cards/{id}/unblock` | Deblokiranje (ADMIN) |
| PATCH | `/cards/{id}/deactivate` | Deaktivacija (ADMIN) |
| PATCH | `/cards/{id}/limit` | Promena limita |
| POST | `/cards/requests` | Zahtev za karticu |
| GET | `/cards/requests/my` | Moji zahtevi |
| GET | `/cards/requests` | Svi zahtevi (ADMIN) |
| PATCH | `/cards/requests/{id}/approve` | Odobravanje |
| PATCH | `/cards/requests/{id}/reject` | Odbijanje |

### Krediti

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/loans` | Zahtev za kredit |
| GET | `/loans/my` | Moji krediti |
| GET | `/loans/{id}` | Detalji kredita |
| GET | `/loans/{id}/installments` | Rate kredita |
| POST | `/loans/{id}/early-repayment` | Prevremena otplata |
| GET | `/loans/requests` | Zahtevi za kredit (ADMIN) |
| PATCH | `/loans/requests/{id}/approve` | Odobravanje |
| PATCH | `/loans/requests/{id}/reject` | Odbijanje |
| GET | `/loans` | Svi krediti (ADMIN) |

### Menjacnica

| Metod | Endpoint | Opis |
|-------|----------|------|
| GET | `/exchange-rates` | Kursna lista |
| GET | `/exchange/calculate` | Kalkulator konverzije |

### Hartije od vrednosti

| Metod | Endpoint | Opis |
|-------|----------|------|
| GET | `/listings` | Lista hartija (sa filterima) |
| GET | `/listings/{id}` | Detalji hartije |
| GET | `/listings/{id}/history` | Istorija cena |
| POST | `/listings/refresh` | Osvezavanje cena (EMPLOYEE) |

### Nalozi za trgovanje

| Metod | Endpoint | Opis |
|-------|----------|------|
| POST | `/orders` | Kreiranje naloga |
| GET | `/orders` | Svi nalozi (ADMIN) |
| GET | `/orders/my` | Moji nalozi |
| GET | `/orders/{id}` | Detalji naloga |
| PATCH | `/orders/{id}/approve` | Odobravanje |
| PATCH | `/orders/{id}/decline` | Odbijanje |

### Aktuari

| Metod | Endpoint | Opis |
|-------|----------|------|
| GET | `/actuaries/agents` | Lista agenata |
| GET | `/actuaries/{employeeId}` | Detalji aktuara |
| PATCH | `/actuaries/{employeeId}/limit` | Postavljanje limita |
| PATCH | `/actuaries/{employeeId}/reset-limit` | Resetovanje limita |

## Seed Data

`seed.sql` popunjava bazu sa test podacima:

| Tip | Kolicina | Login |
|-----|----------|-------|
| Admin korisnici | 2 | `Admin12345` |
| Zaposleni | 5 | `Zaposleni12` |
| Klijenti | 4 (+1 neaktivan) | `Klijent12345` |
| Racuni | 12 klijentskih + 8 bankovnih | - |
| Kartice | 6 (5 aktivnih, 1 blokirana) | - |
| Valute | 8 (EUR, USD, RSD, GBP, CHF, JPY, CAD, AUD) | - |
| Hartije | 5 akcija, 3 forex para, 3 fjučersa | - |

## Autentifikacija

- **JWT HS256** sa access token (15min) + refresh token (7 dana)
- Access token claims: `sub` (email), `role` (ADMIN/CLIENT/EMPLOYEE), `active`
- Refresh na 401 putem `/auth/refresh`
- Role: `ADMIN`, `EMPLOYEE`, `CLIENT`
- Permisije: `TRADE_STOCKS`, `VIEW_STOCKS`, `CREATE_CONTRACTS`, `CREATE_INSURANCE`, `SUPERVISOR`, `AGENT`

## Tim

Projekat razvija Tim 2, Racunarski fakultet Beograd, 2025/26.
