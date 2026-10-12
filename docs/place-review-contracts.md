# Place and review response contracts

`PlaceResponse` defines the DTO for future place search/detail endpoints. No place
endpoint is implemented by this change. Its JSON fields are `place_id`, `name`,
`address`, `latitude`, `longitude`, and `types`; coordinates are decimal degrees
and types use `PlaceType` enum names. Provider integrations must map their own
categories to RESTAURANT, BATHROOM, PARK, ATTRACTION, HOTEL, HOSPITAL, OTHER.
The corresponding frontend PlaceModel uses camelCase `placeId` and copies the
category list when parsing. Its parser checks finite geographic coordinates.

`ReviewResponse` now includes `user_id` alongside `review_id`, `place_id`, `rating`,
`text`, and `created_at`. The response uses the persisted review's author UUID.
Creation still assigns the author from the authenticated JWT; no submission field
or history authorization rule changes. The same DTO is returned by submission,
place reads and current-user history.

Merge/deploy this backend change before the companion frontend model change.
Existing frontend clients tolerate the added response field, while the new
frontend requires it and validates both review and author UUIDs. UUIDs and
Instants are JSON strings on the frontend; Instants retain timestamp precision.

DTO serialization and controller tests verify the wire contract. The backend's
unit/controller suite is run using JDK 21. The separate SpringBootTest context
smoke test requires an available configured OIDC issuer and was not run here.
