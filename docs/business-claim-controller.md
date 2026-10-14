# Business claim controller draft

All endpoints use the existing `/api/v1/**` bearer-token security chain.

| Method | Path | Success | Access |
| --- | --- | --- | --- |
| POST | `/api/v1/business-claims` | 201 with claim JSON | Authenticated user |
| GET | `/api/v1/business-claims/pending` | 200 with a claim array | Administrator |
| POST | `/api/v1/business-claims/{claimId}/approve` | 204, no body | Administrator |
| POST | `/api/v1/business-claims/{claimId}/reject` | 204, no body | Administrator |

Submission body: `{"placeId":"google-place-id"}`. The caller's user ID and
reviewer's admin ID come from `AuthenticationService#requireAuthenticatedUser`
(the authenticated caller's persisted `User.userId`), never from request JSON
or a passed-in JWT. Blank or missing place IDs return 400. Missing
authentication returns 401 (`InvalidTokenException`, mapped by
`ApiExceptionHandler`).

Administrator operations (`GET /pending`, the `approve`/`reject` endpoints)
are marked `@PreAuthorize("hasRole('ADMIN')")` and return 403 unless the
database's `User.admin` column is `true` for the caller - see
`docs/securing-admin-endpoints.md` and `docs/admin-provisioning.md`. This
replaced an earlier draft convention that read a Keycloak realm role
(`admin`, in `realm_access.roles`) directly off the bearer token; the
database is now the single source of truth used everywhere in this
application, admin provisioning included.

## Integration still needed

This change implements the controller layer, not database persistence. Add a
Spring bean implementing `BusinessClaimService`; until then, authorized calls
return 503 rather than reporting an unsaved claim as successful. The minimal
`BusinessClaimRequest` record is a response contract, not a JPA entity.

The service owner should implement place validation, MariaDB/JPA persistence,
creation with PENDING status, user lookup by OIDC subject, and transactional
approval/rejection with reviewer auditing. Missing claims should return 404 and
claims already reviewed should return 409. Service errors propagate so failed
writes cannot produce a success response. Entity approve/reject behavior and the
repository remain outside this controller draft.

The four methods follow the supplied controller/service design and requirements
3.1.6. Administrator operations remain protected even though requirements 2.6
allow postponing their deployment until Keycloak roles are configured.
