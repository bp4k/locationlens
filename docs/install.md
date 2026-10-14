# Backend installation, operation, and handoff

## Prerequisites and dependencies

JDK **21** (check `java -version`), Git, Docker Desktop/engine for Keycloak,
Node.js 22.12+ within 22.x for the paired frontend, and a modern browser.
Use `mvnw.cmd` on Windows or `./mvnw` on Linux/macOS; no global Maven is required.
If the Unix wrapper lacks executable permission, run `chmod +x mvnw`.
Internet access is needed for Maven dependencies, the Keycloak image, and Google.
An 8 GB laptop/desktop is a practical recommendation, not a tested minimum.

The Maven manifest supplies Spring Boot 4.1.1, Spring MVC, Security OIDC client
and JWT resource-server support, Bean Validation, JPA/Hibernate, Lombok, H2,
MySQL Connector/J, Cloud SQL connector and Flyway. H2 is the local/test database;
MySQL/Cloud SQL is the production configuration. Ports 8080, 8084 and 5174 must
be available for the full stack.

## Download

```text
git clone https://github.com/bp4k/locationlens.git
git clone https://github.com/bp4k/locationlens.frontend.git
cd locationlens
```

Use sibling folders and coordinated revisions. Private repos need GitHub access.

## Backend environment reference

Copy [.env.example](../.env.example) to ignored `.env` and load it into the
process as described below. Real secrets must not be committed.

| Variable | Meaning / local value |
| --- | --- |
| `IDP_CLIENT_ID` | Keycloak confidential client ID, `locationlens`. |
| `IDP_CLIENT_SECRET` | Actual secret from that client's Credentials tab. |
| `IDP_URI` | Issuer `http://localhost:8084/realms/locationlens`; must be reachable by backend/browser. |
| `VALID_REDIRECTS` | Comma-separated exact frontend callback URLs, local `http://127.0.0.1:5174/login-redirect`; also derives allowed credentialed refresh origins. |
| `GOOGLE_PLACES_API_KEY` | Server Places API (New) key for search/details and business verification. |
| `GOOGLE_PLACES_BASE_URL` | Optional provider base override for tests; default `https://places.googleapis.com/v1`. |
| `DB_URL` | Default `jdbc:h2:mem:locationlens`; use MySQL JDBC URL for persistence. |
| `DB_USER`, `DB_PASS` | Default H2 user `sa`, empty password; real database credentials for MySQL. |
| `FLYWAY_ENABLED` | `false` for H2; `true` for production MySQL migrations. |
| `DDL_AUTO` | `create-drop` for disposable H2; `validate` with production migrations. |

The application defaults Security logs to TRACE. Avoid sharing credential-bearing
logs; use `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=INFO` outside debugging.

## Start the complete application

Use three running services: Keycloak (8084), backend (8080), and frontend
(127.0.0.1:5174). Start them in that order. Use the same hostname throughout:
`localhost` and `127.0.0.1` are different origins. The frontend address in this
guide is **http://127.0.0.1:5174**.

### 1. Keycloak: first-time setup

Start Docker Desktop (Linux containers) or the Docker engine. The commands below
are each one line and work in PowerShell or Bash:

```text
docker run -d -p 127.0.0.1:8084:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -v kcdata:/opt/keycloak/data --name keycloak quay.io/keycloak/keycloak:26.7.4 start-dev
docker logs --tail 40 keycloak
```

The bootstrap credentials above are for local development only. For subsequent
sessions, reuse the existing container and its saved realm/users:

```text
docker start keycloak
docker ps
```

Wait until http://localhost:8084 responds. Open its admin console with the local
bootstrap credentials, then:

1. Create/select realm `locationlens`.
2. Create an OpenID Connect client with client ID `locationlens`. Enable
   **Client authentication** and **Standard flow** (authorization code).
3. Set Valid redirect URIs to
   `http://localhost:8080/login/oauth2/code/common-idp`. This is the backend
   callback; it is different from the frontend callback below.
4. Copy the client secret from the Credentials tab into the backend `.env`.
5. Create a normal test user with email, first name and last name. Set a password
   with Temporary disabled; complete or disable any outstanding required actions
   for the local test account. Do not use the Keycloak admin-console account as
   your application's ordinary test user.
6. Optional: enable realm User registration to let users create their own accounts
   on the Keycloak login page. No separate frontend registration form is supplied.

For administrator claim testing, create realm role `locationlens_admin` and assign
it to a separate test user. Ensure the client's `roles` client scope is attached,
and its realm-role mapper includes `realm_access.roles` in the **ID token**
("Add to ID token" enabled). A normal token role or a visible admin menu alone is
insufficient. The login success handler synchronizes that role into `User.admin`;
API authorization then reads the database. Sign in again after role changes.

The Sign out UI is currently removed. Use separate browser profiles for user/admin
testing, or close all private-browsing windows and start a fresh private session
to test another login. Clearing frontend storage alone does not end Keycloak SSO.

### 2. Google configuration

Provision a Google Cloud project with billing and enable **Places API (New)**
for backend search/details/claim checks and **Maps JavaScript API** for the
browser map. Supply a server Places key through `GOOGLE_PLACES_API_KEY` and a
browser Maps key through `VITE_GOOGLE_MAPS_API_KEY`; restrict them for their
respective services and use. Do not put a server credential into a `VITE_*` value.
The browser key is visible in the built application.

Allow the frontend origin in the browser key's HTTP referrers, for example
`http://127.0.0.1:5174/*`, plus the deployed frontend origins. Provide a configured
map ID in `VITE_GOOGLE_MAPS_MAP_ID`. The code falls back to `DEMO_MAP_ID` for
development, but deployment validation requires a map ID value. Without the map
key, the text results remain usable; without the server Places key, live search,
provider-backed details, and business verification are unavailable.

### 3. Backend terminal

From the backend repository, copy `.env.example` to `.env` and replace its
placeholder client secret and server Places key. Spring does **not** automatically
read this file; load it into the same terminal that starts Maven.

PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env before continuing; do not overwrite an existing configured file.
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#")) {
        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            $value = $parts[1].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $value, "Process")
        }
    }
}
.\mvnw.cmd spring-boot:run
```

Bash (Linux/macOS):

```bash
cp .env.example .env
# Edit .env first. Use shell-compatible values; quote values containing spaces.
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

These examples assume one KEY=VALUE per line, without inline comments. Wait for
the Spring application-started message and HTTP port 8080. Leave it running.

### 4. Frontend terminal

From the frontend repository:

```text
npm ci
```

Copy `.env.example` to `.env` using `Copy-Item` (PowerShell) or `cp` (Bash), and
edit the browser Maps settings. Preserve existing configuration instead of
overwriting it. Start the frontend:

```text
npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Open **http://127.0.0.1:5174/welcome**. The Vite Local URL indicates the server is
ready; initial dependency optimization can make the first load slower.
`VITE_APP_API_URL` must be `http://localhost:8080/api` with no trailing slash.
Backend `VALID_REDIRECTS` must include the exact
`http://127.0.0.1:5174/login-redirect` target. Restart Vite after environment changes;
reload the backend environment and restart Spring after backend changes.

### 5. Acceptance check and shutdown

1. Sign in as the normal test user; confirm account information is available.
2. Search Atlanta with radius 1000; try a coordinate search including valid zero
   values, and a category filter. Radius must be 1–50,000 meters.
3. Select a list result and a marker; confirm shared highlight and details.
4. Submit a rating (1–5) and nonblank review (at most 2,000 characters); check
   place reviews and My reviews. One review per user/place is enforced.
5. Claim an actual business result. Use the administrator profile to review a
   pending claim. Place verification establishes existence; approval is a human
   ownership decision. Do not test this with fabricated place IDs.
6. Confirm ordinary users cannot approve/reject claims even if they see the menu.

Press Ctrl+C in frontend and backend terminals. If Windows asks to terminate the
batch job, answer `Y`. Optionally run `docker stop keycloak`. Do not delete the
container/volume just to end a session. Default backend H2 data is erased on
restart; Keycloak data in the named Docker volume is separate and persists.

## Troubleshooting

| Symptom | Check and corrective action |
| --- | --- |
| Docker engine pipe missing / cannot connect | Start Docker Desktop and wait for the Linux engine. |
| Container name already in use | Run `docker ps -a`; reuse `docker start keycloak` rather than creating another. |
| Keycloak page unavailable | Check container status/port 8084 and `docker logs --tail 40 keycloak`; wait for startup. |
| `/undefined/v1/login` or a route error containing undefined | Set frontend `VITE_APP_API_URL`, then restart Vite. |
| `invalid_client` / invalid client credentials | Match backend client ID/secret to the configured realm/client; reload `.env` and restart backend. Restarting Docker does not load backend environment values. |
| Login returns token JSON or rejects target | Set backend `VALID_REDIRECTS` to the exact frontend `/login-redirect` URL; avoid hostname/port mismatch. |
| Keycloak invalid redirect URI | Login callback belongs to backend `/login/oauth2/code/common-idp`; post-logout `/welcome` has a separate Keycloak allow-list. |
| Search 404 | Inspect Network URL: current route is `/api/v1/places/search` (plural); ensure paired current revisions and correct API base. |
| Search 400 | Use exactly one location mode, coordinates in range, radius 1–50,000, and supported uppercase category. |
| Search/claim 503 or provider failure | Set server Places key, enable Places API (New)/billing, and check provider restrictions/quota and backend logs. |
| Map fails but list works | Check browser Maps key, Maps JavaScript API, billing/referrers/map ID and console messages; restart/rebuild frontend after changes. |
| API 401 | Sign in again in a fresh profile; check token expiry, reachable issuer and backend session. A direct API request needs a bearer token. |
| Admin API 403 | Check `locationlens_admin` role and ID-token realm-role mapper, then sign in again to synchronize database status. |
| Session refresh CORS/cookie failure | The refresh endpoint uses credentials and only origins derived from `VALID_REDIRECTS`. Check exact origin, HTTPS/cookie policy and browser third-party-cookie restrictions. |
| Review 409 | A review already exists for that user/place; editing/deletion is not provided. |
| Claim conflict | The claim may already have been reviewed; refresh pending claims instead of repeating a decision. |
| Port already in use | Stop the previous dev process; changing ports also requires updating callback/origin configuration. |
| Successful deploy but old UI | CD uses `promote: false`; test the reported version URL and explicitly migrate traffic after verification. |
| Local reviews/claims disappear | H2 memory + create-drop is disposable; configure persistent MySQL and migrations. |

## Repository access and submission

Repository owners/admins should open GitHub **Settings → Collaborators / Manage
access** for **both** repositories, invite `Nimisha-Roy` and the assigned mentor
by exact GitHub username, and confirm invitation acceptance before submitting
the two repository links. Use a read-capable role sufficient for assessment;
available roles depend on repository ownership. If invitations were already sent,
verify both assessors appear in the access list rather than only as pending invites.
Record the final frontend/backend commit IDs, successful CI/CD run links, and
deployed URLs in the submission. Branch protection requires PRs; force push does
not bypass a required-PR rule.

## Build and verification

With Keycloak running and the environment loaded, build/test the full application:

PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Bash:

```bash
./mvnw clean verify
```

This includes the application context test, so valid reachable issuer/client
configuration is required. For CI-style isolated testing without live Keycloak:

PowerShell:

```powershell
$env:DB_URL = 'jdbc:h2:mem:locationlens-tests;DB_CLOSE_DELAY=-1'
$env:DB_USER = 'sa'
$env:DB_PASS = ''
$env:FLYWAY_ENABLED = 'false'
$env:DDL_AUTO = 'create-drop'
.\mvnw.cmd '-Dtest=*Test,*Tests,!LocationlensApplicationTests' verify
```

Bash:

```bash
DB_URL='jdbc:h2:mem:locationlens-tests;DB_CLOSE_DELAY=-1' DB_USER=sa DB_PASS='' FLYWAY_ENABLED=false DDL_AUTO=create-drop ./mvnw '-Dtest=*Test,*Tests,!LocationlensApplicationTests' verify
```

These temporarily override the process database settings. Reload the intended
environment before starting the application afterward. Excluding the context
test does not demonstrate real authentication or production-database readiness.

The packaged executable is `target/locationlens-0.0.1-SNAPSHOT.jar`:

```text
java -jar target/locationlens-0.0.1-SNAPSHOT.jar
```

The same environment must be loaded for JAR execution. Controller, service,
repository, provider-adapter and integration tests are under `src/test/java`;
results are written to `target/surefire-reports`.

## Persistent MySQL setup

Use an empty database on a MySQL 8.x instance and a database user able to apply
the initial migrations. In the MySQL client, an administrator can create these
with appropriately chosen credentials:

```sql
CREATE DATABASE locationlens CHARACTER SET utf8mb4;
CREATE USER 'locationlens'@'localhost' IDENTIFIED BY '<choose-a-password>';
GRANT ALL PRIVILEGES ON locationlens.* TO 'locationlens'@'localhost';
```

For a remote database configure the matching host/network access instead of
blindly reusing `localhost`. Set:

```ini
DB_URL=jdbc:mysql://localhost:3306/locationlens
DB_USER=locationlens
DB_PASS=<matching-password>
FLYWAY_ENABLED=true
DDL_AUTO=validate
```

Flyway applies V1 baseline schema and V2 place tables; Hibernate validates the
schema. Do not use `create-drop` against persistent customer data. Existing
nonempty databases need a reviewed migration/baseline plan and a backup; the
enabled baseline-on-migrate setting is not a substitute for that review.

## CI/CD and production setup

`java-ci.yml` tests master pushes/PRs using H2 and excluding the issuer-dependent
application context test. `java-cd.yml` is manually dispatched: Maven verify →
runtime configuration/JAR staging → Google authentication → App Engine version.
`java-cloud.yml` manually checks Cloud SQL connectivity; it does not run the UI.

Configure the repository's `production` environment:

| Kind | Names |
| --- | --- |
| Variables | `GCP_PROJECT_ID`, `CLOUD_SQL_INSTANCE` (`project:region:instance`), `CI_DB_NAME`, `GCP_RUNTIME_SERVICE_ACCOUNT`; optionally `IDP_URI`, `IDP_CLIENT_ID`, `VALID_REDIRECTS` |
| Secrets | `GCP_SA_KEY`, `CI_DB_USERNAME`, `CI_DB_PASSWORD`, `IDP_CLIENT_SECRET`, `GOOGLE_PLACES_API_KEY`; `IDP_URI`, `IDP_CLIENT_ID`, `VALID_REDIRECTS` can instead be secrets |

Provision an initialized Google App Engine Java 21 project, a Cloud SQL MySQL
instance/database/user, and a reachable HTTPS Keycloak realm. Grant the deploying
service account deployment permissions and permission to use the configured
runtime service account; grant the runtime account Cloud SQL connection access.
The preparation script requires a PUBLIC connector path (`ipTypes=PUBLIC`), so
provision a compatible Cloud SQL instance rather than assuming private-only
networking will work with the checked-in setup.

`.github/scripts/prepare-appengine.rb` validates settings, constructs the connector
JDBC URL, and creates untracked `target/appengine/app.yaml` plus `app.jar`. It
requires Ruby when run outside the GitHub runner. Generated configuration holds
runtime secrets and is cleaned by the workflow; do not publish it as an artifact.
`app.yaml` selects Java 21, service `default`, Flyway enabled and schema validation.

Run **Deploy Backend to App Engine** from the reviewed branch, then test the URL
reported in its summary. **promote: false** leaves traffic unchanged. Configure
Keycloak's valid login redirect to the intended backend HTTPS
`/login/oauth2/code/common-idp`, configure `VALID_REDIRECTS` for each exact frontend
`/login-redirect`, and rebuild the frontend with the intended HTTPS backend
`/api` base. Confirm user/admin workflows before explicitly migrating traffic.
Session refresh relies on browser cookies, so verify cookie settings and allowed
origins in the deployed domain arrangement.

After acceptance, use Google Cloud Console → App Engine → Versions, select the
relevant service/version, and migrate or split traffic deliberately. Do this for
both backend (`default`) and frontend (`frontend`) services as appropriate. Keep
the previous compatible version available for rollback and verify both final
public URLs afterward; a version-specific URL alone is not the customer release.

## Architecture and supported contracts

Controllers receive HTTP; services apply rules; repositories persist entities.
Google adapter classes translate provider data. OIDC login synchronizes users;
Spring validates API JWTs; database admin status enforces claim decisions.
See [API reference](api.md) and [release notes](../RELEASE_NOTES.md).
