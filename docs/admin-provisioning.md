# Admin provisioning from Keycloak

The database's `User.admin` column is the source of truth for whether a user
is an admin. It is (re)written on every login, not just the first one, from
whether the login's `realm_access.roles` claim contains the Keycloak realm
role `locationlens_admin`. A user who had the role revoked in Keycloak loses
admin status in the database the next time they log in; it does not persist
indefinitely once granted.

## Where this happens

`IdpTokenRelaySuccessHandler.syncLocalUser` (called from
`onAuthenticationSuccess`, before either of its existing branches) reads the
login's `OidcUser` principal and calls `hasLocationlensAdminRole`, which
checks:

```json
{"realm_access": {"roles": ["locationlens_admin"]}}
```

The result is passed as the new `isAdmin` parameter on
`AuthenticationService.createOrUpdateUser(subject, email, givenName,
familyName, isAdmin)`, which now calls `user.setAdmin(isAdmin)` unconditionally
on every call, whether creating or updating.

## Keycloak configuration this depends on

- A realm role named exactly `locationlens_admin` must exist, assigned to
  whichever users should be admins (Admin Console → Realm roles / Users →
  Role mapping).
- The `common-idp` client's `roles` client scope mapper must have **"Add to
  ID token" enabled**. This handler reads the claim off the `OidcUser`
  principal, which is built from the ID token (and userinfo), not the access
  token — if the mapper only adds the claim to the access token, this sync
  will never see the role and every login will be provisioned as a
  non-admin.

This `locationlens_admin` realm role is consulted exactly once, here, at
login time, to decide what to write into `User.admin`. It is unrelated to -
and, since `docs/securing-admin-endpoints.md`, no longer the same mechanism
as - `BusinessClaimController`'s admin check, which used to read a different
realm role (`admin`) directly off the bearer token at request time. That
controller (and every other admin check in this application) now consults
`User.admin` instead, via `AuthenticationService#isAdmin` /
`AdminAuthoritiesConverter`, so the database this document describes is the
single source of truth used everywhere, not just here.

## Tests

`AuthenticationServiceTest` gained four cases: a new user provisioned
admin/non-admin, and an existing user promoted/demoted on a subsequent login
when the role appears/disappears.

`IdpTokenRelaySuccessHandlerTest` gained cases for: the role present, the role
absent among other roles, and the `realm_access` claim missing entirely (must
not throw, must provision as non-admin). The existing sync tests were updated
for the new `isAdmin` parameter.
