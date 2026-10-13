# Securing admin-only endpoints

Any endpoint under `/api/v1/**` can require admin access with a single
annotation:

```java
@GetMapping("/api/v1/admin-only")
@PreAuthorize("hasRole('ADMIN')")
public String adminOnly() {
    return "I am an Admin";
}
```

(This exact example lives at `SampleController#adminOnly`.)

## How it works

- `AdminAuthoritiesConverter` (`net.bp4k.locationlens.security`) is wired
  into `apiSecurityFilterChain`'s `oauth2ResourceServer(oauth2 ->
  oauth2.jwt(...))` configuration in `SecurityConfig`, as the
  `JwtAuthenticationConverter`'s granted-authorities converter. On every
  request, it calls `AuthenticationService#isAdmin(jwt.getSubject())` - a
  fresh database lookup, never anything read off the token - and grants
  `ROLE_ADMIN` if, and only if, that returns `true`.
- `SecurityConfig` is annotated `@EnableMethodSecurity`, which is what makes
  `@PreAuthorize` take effect on any Spring-managed bean's methods, not just
  `SampleController`'s.
- A caller with no valid bearer token never reaches the annotation at all:
  `apiSecurityFilterChain`'s `.anyRequest().authenticated()` already
  rejects it with `401` first.
- A caller with a valid token but no `ROLE_ADMIN` authority is rejected by
  `@PreAuthorize` with `AccessDeniedException`, which
  `ApiExceptionHandler#handleAccessDenied` maps to `403` with a JSON
  `ErrorResponse` body (previously a bare `403`, Spring Security's default).

The database (`User.admin`) is the only source of truth consulted here - see
`docs/admin-provisioning.md` for how that column gets set from Keycloak at
login time, and `docs/business-claim-controller.md` for the controller this
replaced an older, JWT-claim-based admin check in.

## Where this can't be used as-is: identity-aware controllers

`@PreAuthorize` is declarative and only works through a real Spring AOP
proxy - it is inert on a plain, directly-constructed object (which is why
unit-testing it needs the pattern described below). `BusinessClaimController`
uses it on its three admin-only endpoints (`GET /pending`, the
`approve`/`reject` endpoints) the same way `SampleController` does. For
identity (not authorization) inside a method body, controllers call
`AuthenticationService#requireAuthenticatedUser()` directly rather than
taking a `Jwt` parameter - see `ReviewController` and `BusinessClaimController`
for examples. These are two independent concerns: `@PreAuthorize` decides
*whether* the method runs; `requireAuthenticatedUser()` resolves *who* is
calling once it does.

## Testing `@PreAuthorize` without a full Spring context

A plain unit test that constructs a controller directly
(`new SampleController()`) and calls `controller.adminOnly()` will **not**
enforce `@PreAuthorize` - there is no proxy intercepting the call. To test
the real enforcement without paying for a full `@SpringBootTest`, wrap the
target in a proxy that applies the same interceptor
`@EnableMethodSecurity` would:

```java
private static SampleController securedProxy(SampleController target) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
    return (SampleController) factory.getProxy();
}
```

Then set `SecurityContextHolder`'s `Authentication` directly (e.g. a
`TestingAuthenticationToken` with or without `ROLE_ADMIN`) before calling the
proxy - no mocked `Jwt`, `UserRepository`, or `AdminAuthoritiesConverter`
needed, since the proxy only cares about the authorities already present on
the `Authentication`. See `SampleControllerTest` and the
`securedProxy`/`authenticateAsAdmin` helpers in `BusinessClaimControllerTest`
for worked examples, including the one case (no `Authentication` at all) that
throws `AuthenticationCredentialsNotFoundException` instead of
`AccessDeniedException` - a corner case that cannot happen in production,
since `apiSecurityFilterChain` rejects an unauthenticated request before
method security ever runs.

`AdminAuthoritiesConverter` itself (the database lookup) is tested
separately and more simply, in `AdminAuthoritiesConverterTest`, by mocking
`AuthenticationService#isAdmin` directly - no proxy needed there, since it's
a plain `Converter`, not an annotated method.
