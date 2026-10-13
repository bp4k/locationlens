# Place service

`PlaceService.addPlace(placeId, name, address, latitude, longitude, types)`
persists a new `Place`. It rejects the request with `DuplicatePlaceException`
(mapped to 409 Conflict by `ApiExceptionHandler`, matching `DuplicateReviewException`)
in two cases:

- A place with the same `placeId` already exists, regardless of its other
  fields.
- A place with the same `name`, `address`, `latitude`, `longitude`, and
  `types` already exists under a different `placeId`. Two places with the
  same name/address/location but different `types` are not considered
  duplicates.

As a defense against a race between the existence check and the insert, the
save is also wrapped to translate a `DataIntegrityViolationException` (a
primary key collision on `placeId`) into the same `DuplicatePlaceException`,
following the pattern `ReviewService` uses for `DuplicateReviewException`.

## Repository support

`PlaceRepository.findByNameAndAddressAndLatitudeAndLongitude` was added to
support the attribute-duplicate check. It does not take `types`, because
Spring Data cannot derive set-equality against the `types`
`@ElementCollection` from a method name; `PlaceService` instead loads the
name/address/location matches and compares `types` in memory. This is
expected to be a small result set in practice, since name/address/location
collisions should be rare.

## Tests

`PlaceServiceTest` covers: a successful add, the duplicate-ID rejection, the
duplicate-attributes rejection, that differing `types` is *not* treated as a
duplicate, and the `DataIntegrityViolationException` translation path.
