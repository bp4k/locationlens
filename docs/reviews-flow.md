# Place selection and review flow

The frontend requests GET /api/v1/places/{placeId} before opening the selected
place's review panel. The former controller always returned an empty HTTP 400,
which prevented that panel from appearing.

PlaceController now returns PlaceResponse with HTTP 200 for a database place or
one of the existing search sample results, and HTTP 404 for an unknown ID. Database
records take priority. PlaceResponse.from maps the entity to the frontend's exact
snake_case DTO, including deterministic category ordering.

Search still returns the team's sample catalog (dummy-1, dummy-2, dummy-3). This
change shares that catalog between search and details; it does not implement live
Google search, geocoding, or search filtering. The sample IDs are not Google IDs.
They support the review demonstration, not provider-verified business claims.

Review submission and history require a local user, provisioned by the existing
OIDC success handler. The frontend sends place_id/rating/text; the backend supplies
author UUID, review UUID and timestamp. Creation returns 201; place/history reads
return 200; repeated user/place reviews return 409. Current-user history excludes
other users' reviews. Edit/delete endpoints remain separate future work.

Verification includes all backend tests except the externally configured context
smoke test, real JPA/controller review integration tests, and the companion browser
suite. The latter uses real Spring Security authorization-code/JWT processing,
services and H2, with a controlled OIDC issuer rather than live Keycloak.

Run backend tests with Java 21, FLYWAY_ENABLED=false, DDL_AUTO=create-drop and an
isolated H2 database as in Java CI. For browser verification, package the backend
and set E2E_BACKEND_DIR to this checkout before running the frontend test:e2e script.

The existing default DDL_AUTO=create-drop and in-memory H2 are development settings.
A persistent deployment must supply a persistent DB_URL and an appropriate migration/
DDL policy; browser reload persistence does not imply survival of a server restart.
This PR does not change the team's database deployment configuration.
