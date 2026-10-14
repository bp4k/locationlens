# Place search service

`PlaceSearchService.search` validates that the latitude/longitude/radius group
is either fully provided or fully `null`, throwing `IllegalArgumentException`
when only some of the three are set. `getPlaceDetails` returns `null` for an
unknown place ID rather than throwing.

## Known gap: unmapped IllegalArgumentException

`ApiExceptionHandler` has no `@ExceptionHandler` for `IllegalArgumentException`.
If a controller calls `PlaceSearchService.search` with a partial location
group, the exception is uncaught and Spring's default handling returns a bare
500 Internal Server Error instead of a 400 with a useful message, unlike
`InvalidTokenException` (401) and `DuplicateReviewException` (409), which are
both mapped.

Whoever wires up the places controller should add a handler, e.g.:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleInvalidParameter(IllegalArgumentException e) {
    return build(HttpStatus.BAD_REQUEST, e.getMessage());
}
```

Note that `IllegalArgumentException` is a common, broad JDK exception type;
mapping it globally could catch unrelated bugs and report them as 400s instead
of 500s. Consider whether that tradeoff is acceptable, or scope the handler
more narrowly (e.g. catch it only at the controller method level) before
adding it.
