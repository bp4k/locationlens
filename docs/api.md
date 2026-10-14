# Backend API reference

Base: `http://localhost:8080/api/v1` locally; HTTPS backend `/api/v1` when deployed.
Except login/session refresh, requests require `Authorization: Bearer <access token>`.
JSON submissions need `Content-Type: application/json`. User identity comes from
validated authentication, not a user ID in the request body.

| Method/path | Input | Result / access |
| --- | --- | --- |
| GET `/login` | Optional `target` URL matching `VALID_REDIRECTS` exactly | Starts OIDC login; relays token to frontend callback or JSON when no target. |
| POST `/session/refresh` | Existing OIDC session cookie; approved frontend Origin | Bearer token response; refresh token stays server-side. |
| GET `/me` | Bearer token | Current user; see controller DTO. |
| GET `/places/search` | `radius_meters`; either `location_text` or complete `latitude`/`longitude`; optional `place_type` | Array of place responses. |
| GET `/places/{placeId}` | URL-encoded provider ID | Place response (local record or Google details). |
| POST `/reviews` | `{"place_id":"provider-id","rating":5,"text":"A useful review"}` | 201 review response; 409 for duplicate user/place. |
| GET `/reviews/place/{placeId}` | URL-encoded place ID | Array of place reviews. |
| GET `/reviews/history` | Current authenticated user | Array of that user's reviews. |
| POST `/business-claims` | `{"placeId":"provider-id"}` | Created pending claim; verifies place/business first. |
| GET `/business-claims/pending` | Admin bearer token | Pending claims. |
| POST `/business-claims/{claimId}/approve` | Admin; canonical claim UUID | 204, no JSON body. |
| POST `/business-claims/{claimId}/reject` | Admin; canonical claim UUID | 204, no JSON body. |

Search radius is an integer **1–50,000 meters**; text has at most 500 characters.
Latitude is −90..90, longitude −180..180; zero is valid. Supply exactly one location
mode. Categories: `RESTAURANT`, `BATHROOM`, `PARK`, `ATTRACTION`, `HOTEL`, `HOSPITAL`,
`OTHER`. Text search first resolves a location with Places searchText and then
performs searchNearby; at most 20 results are returned. Provider categories are
translated into application categories.

Place JSON example (illustrative values, not a valid business test ID):

```json
{"place_id":"provider-id","name":"Example Park","address":"Atlanta, GA","latitude":33.78,"longitude":-84.37,"types":["PARK"]}
```

Review rating is an integer 1–5 and text is nonblank with at most 2,000 characters.
Claim response JSON uses `id`, `userId`, `placeId`, `status` (the frontend maps
`id` to `claimId`); statuses are `PENDING`,
`APPROVED`, `REJECTED`. Claim decisions are admin-only and may conflict if already
reviewed. A place ID is an opaque provider string, not a UUID; claim/user IDs are UUIDs.

To inspect a request in PowerShell, obtain your own access token through a normal
login, then set `$accessToken` locally without committing it:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/places/search?location_text=Atlanta&radius_meters=1000&place_type=PARK' -Headers @{Authorization="Bearer $accessToken"}
```

Common status meanings: 400 invalid input, 401 missing/expired authentication,
403 insufficient permission, 404 unavailable resource/incorrect route, 409 state
conflict, 503 external service/configuration unavailable. Error bodies may use
`message` or Problem Detail `detail`; clients should preserve status and tolerate
non-JSON failures. Endpoint definitions/DTOs remain the source of truth.
