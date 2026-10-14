# LocationLens backend

Java 21 / Spring Boot backend for place discovery, authentication, reviews and
business claims. It integrates Keycloak, Google Places API (New), JPA persistence,
and MySQL/Cloud SQL production configuration.

## Customer/developer handoff

- [Release notes, fixes and known limitations](RELEASE_NOTES.md)
- [Installation, configuration, build/run, testing, database setup, deployment and troubleshooting](docs/install.md)
- [Environment template](.env.example)
- [API reference and request/response examples](docs/api.md)
- [Paired frontend repository](https://github.com/bp4k/locationlens.frontend)

Start with the installation guide. It includes PowerShell and Bash commands,
first-time Keycloak/client/admin setup, Google configuration, and exact frontend
callback origins. Spring does not automatically read `.env`; load it before Maven.

Local ports: backend 8080, Keycloak 8084, frontend 5174. H2/create-drop defaults are
disposable. The paired frontend's Sign out UI is currently removed while deployed
logout is investigated.

For a configured full build use `.\mvnw.cmd clean verify` (Windows) or
`./mvnw clean verify` (Linux/macOS). See the guide for isolated CI-style testing
and its excluded context test. Deployment is manual and does not promote traffic.
