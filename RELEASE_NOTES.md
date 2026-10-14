# Project 1 release notes

Release snapshot: October 14, 2026. Pair this backend with the corresponding
frontend release and verify the serving versions before customer handoff.

## Delivered functionality

- Keycloak authorization-code login, user synchronization, JWT-protected REST API,
  account information, and backend-assisted access-token renewal.
- Google Places (New) text/coordinate nearby search and place details.
- Location type translation/filtering and validated search criteria.
- **Additional feature 1:** review/rating submission, place reviews and current-user history.
- **Additional feature 2:** verified-place business claims, pending-claim retrieval,
  and database-authorized administrator approval/rejection.
- JPA persistence, H2 development/testing, MySQL/Cloud SQL production configuration,
  and Flyway schema migrations.
- Java CI, manual App Engine deployment, and a Cloud SQL connectivity workflow.

## Significant corrections

- Replaced placeholder place responses with provider-backed search/details and a
  consistent `/api/v1/places` contract.
- Derive reviewer/claimant identity from authentication rather than client-supplied IDs.
- Enforce one review per user/place and review input validation.
- Verify Google place/business targets before claim storage and guard invalid claim decisions.
- Use transactional/locked claim decisions to protect simultaneous state changes.
- Renew access tokens with server-side refresh credentials and restricted credentialed origins.
- Synchronize the Keycloak admin realm role on login; use database admin status for requests.

## Known defects, limits, and incomplete functionality

- The paired frontend's deployed logout issue remains unresolved and its Sign out
  menu item is removed. No complete backend/Keycloak logout fix is claimed.
- Default H2/create-drop data is disposable; production persistence needs the
  documented MySQL/Cloud SQL credentials, permissions and migration setup.
- Places search caps nearby results at 20; there is no pagination.
- Reviews do not supply edit/delete operations; claims record decisions rather than
  implementing a complete verified-owner business-management portal.
- General bearer API CORS currently allows all origins; session refresh uses the
  narrower configured frontend-origin list. Review production policy deliberately.
- Admin changes are synchronized on login; changing a Keycloak role requires a new login.
- CI excludes the live-issuer application context smoke test. Fixtures and isolated
  tests do not establish production database or live-provider readiness.
- CD is manual and uses `promote: false`; a successful run may leave public traffic
  on an older version.

## Verification boundary

Controller, service, repository, provider-flow and integration test source is
included. Backend tests were inspected during documentation preparation, but no
fresh backend run or exact passing test count is asserted. Run the commands in
[installation guide](docs/install.md) and retain CI/live acceptance evidence.
