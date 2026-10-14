# Syncing the local User row on OIDC login

`AuthenticationService.createOrUpdateUser(subject, email, givenName, familyName)`
existed before this change, but nothing in production called it: neither the
`oauth2Login()` success path, the `/api/v1/**` resource-server JWT chain, nor
`GET /api/v1/me` (which reads claims straight off the raw `Jwt`) ever created
or updated a local `User` row. A user could complete OIDC login and call the
API indefinitely without ever having a corresponding row in the `user` table.

## The fix

`IdpTokenRelaySuccessHandler.onAuthenticationSuccess` is the single place both
of this app's logins funnel through after Spring Security has already
validated the login with the IDP: a browser hitting a session-protected page,
and a client that called `GET /api/v1/login` for a bearer token. A new private
method, `syncLocalUser(authentication)`, runs first, before either of the
existing branches (page redirect vs. token relay):

- It reads the authentication's principal as an `OidcUser` and calls
  `AuthenticationService.createOrUpdateUser` with the standard `sub`,
  `email`, `given_name`, and `family_name` claims.
- `oauth2Login()` only ever invokes this handler with an
  `OAuth2AuthenticationToken` whose principal is an `OidcUser`, because the
  `common-idp` client registration requests the `openid` scope (see
  `application.properties`). The `instanceof OidcUser` check is defensive,
  not load-bearing: if that ever stopped being true, this method would
  simply skip syncing rather than throw and break login.

This keeps `AuthenticationService` itself unchanged; the gap was purely that
nothing called it.

## Tests

`IdpTokenRelaySuccessHandlerTest` was updated:

- `newHandler()` now wires a mock `AuthenticationService` into the handler
  (overloaded so existing tests can keep using the no-arg form).
- A new `oidcAuthentication(subject, email, givenName, familyName)` overload
  stubs an `OAuth2AuthenticationToken` whose principal is a mocked `OidcUser`
  carrying those claims.
- `syncsLocalUserFromOidcClaimsOnEveryLogin` asserts `createOrUpdateUser` is
  called with the right claims, using the non-API (page-redirect) branch, to
  confirm syncing isn't tied to one branch.
- `doesNotSyncWhenPrincipalIsNotAnOidcUser` asserts `createOrUpdateUser` is
  never called when the principal isn't an `OidcUser` (a plain mock
  `Authentication`, whose principal is `null` by default).
