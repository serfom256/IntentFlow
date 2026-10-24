# IntentFlow Admin

A web UI, embedded in your application, for changing at runtime what your annotations and builders declared:

- service descriptions and field descriptions, which are what the LLM reads
- service paths (`uri`), which are the links returned with each answer

"Try a question" shows the DSL and SQL a question produces with the current descriptions, without running the
query. With a PostgreSQL or MySQL `DataSource` it also shows the database's plan (`EXPLAIN`, which does not run the
query either) and suggests indexes: on the columns a full scan filters or sorts by, an expression index for
case-insensitive matches, and a trigram index for contains searches on PostgreSQL. Columns an existing index already
covers are skipped. Review a suggestion before applying it: a planner often prefers a full scan on small tables.

## Setup

```kotlin
implementation("io.github.serfom256:intentflow-admin-spring-boot-starter:0.5.0")
```

That's all: the UI opens at `http://<host>:<port>/intentflow-admin/`. It is a static bundle inside the jar, served
by your application, so there is no separate server or Node.js. The starter brings the IntentFlow starter with it.

## Editing

For each service the UI shows the declared values next to your overrides. Before an edit is stored it is checked:
descriptions must be single lines of at most 1000 characters, field names must exist, and every `{{placeholder}}`
in a path must be a field of the service. "Reset to declared" removes a service's overrides.

A change applies to the next question. Translations made with the old descriptions are not reused.

## Security

The admin changes what is sent to the LLM, so it is protected by default. Which protection applies depends on your
application:

| Your application              | Who gets in                                                                  |
| ----------------------------- | ---------------------------------------------------------------------------- |
| uses Spring Security          | editors with one of `intentflow.admin.security.authorities` (default `ROLE_ADMIN`); read-only users with one of `read-only-authorities` |
| does not use Spring Security  | HTTP Basic: user `admin`, password from `intentflow.admin.security.password`, or a generated one printed in the startup log |
| defines an `AdminAccess` bean | whoever that bean allows                                                     |

**With Spring Security** the admin authenticates nobody itself. It reads the `Authentication` your security set up,
so any login works: form login, OAuth2/OIDC, LDAP, JWT, a custom `AuthenticationProvider` or filter. Make sure your
security lets requests to `/intentflow-admin/**` through to it, authenticated or not, and give admin users the
required authority:

```yaml
intentflow:
  admin:
    security:
      authorities: ROLE_ADMIN, SCOPE_intentflow.admin
      login-url: /login
```

When nobody is signed in, the UI offers a link to `login-url` and a field for a bearer token, for applications that
authenticate API calls with tokens. Changes are sent with your application's CSRF token, so CSRF protection can stay
on.

**Read-only users** with one of `read-only-authorities` see the catalog and explain questions, but cannot save or
reset an override: the UI shows the form disabled and the API answers 403 `read-only`.

**Single sign-on with OIDC** (Keycloak, Entra ID, Okta, Google Workspace) is Spring Security's OAuth2 login with
`spring-boot-starter-oauth2-client`. Register the provider, map the role claim of the ID token to authorities, and
point the admin at the login:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          corporate-sso:
            client-id: intentflow-admin
            client-secret: ${OIDC_CLIENT_SECRET}
            scope: openid, profile
        provider:
          corporate-sso:
            issuer-uri: https://sso.example.com/realms/main
intentflow:
  admin:
    security:
      authorities: ROLE_intentflow-editor
      read-only-authorities: ROLE_intentflow-viewer
      login-url: /oauth2/authorization/corporate-sso
```

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(auth -> auth.requestMatchers("/intentflow-admin/**").permitAll()
                    .anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults())
            .build();
}

@Bean
GrantedAuthoritiesMapper oidcRoles() {
    return authorities -> {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                List<String> roles = oidc.getIdToken().getClaimAsStringList("roles");
                if (roles != null) {
                    roles.forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
            }
        }
        return mapped;
    };
}
```

The admin does its own checks, which is why the filter chain lets `/intentflow-admin/**` through. Which claim holds
the roles depends on the provider: Entra ID app roles and Okta custom claims usually use `roles`, Keycloak puts realm
roles under `realm_access.roles` unless a mapper flattens them, and groups arrive as `groups`. The user name shown in
the UI and written to the audit log is the principal's name; set `user-name-attribute` on the provider (for example
`preferred_username`) to show a login rather than the subject id.

**Without Spring Security** the starter does not add it, because Spring Security would lock your whole application.
Only the admin is protected, with HTTP Basic. Set a password for anything beyond development:

```yaml
intentflow:
  admin:
    security:
      password: ${INTENTFLOW_ADMIN_PASSWORD}
```

**Your own rule**, e.g. a claim, a tenant or the client network, is an `AdminAccess` bean:

```java
@Bean
AdminAccess adminAccess() {
    return request -> request.isUserInRole("CATALOG_EDITOR")
            ? AdminAccess.Decision.allowed(request.getRemoteUser())
            : AdminAccess.Decision.forbidden(request.getRemoteUser());
}
```

`intentflow.admin.security.enabled=false` opens the admin to everyone, for local development only.

## Storage

With a `DataSource`, overrides are stored in the table `intentflow_catalog_override`, created on startup if missing.
Each instance re-reads it every `refresh-interval`, so all instances pick up a change within that time. Without a
`DataSource`, or with `storage: memory`, overrides live in memory and are lost on restart. A `CatalogOverrides` bean
replaces both.

Outside Spring, `ServiceCatalogs.withOverrides(catalog, overrides)` applies overrides to any catalog.

## Configuration

| Property                                | Default                     | Description                                          |
| --------------------------------------- | --------------------------- | ---------------------------------------------------- |
| intentflow.admin.enabled                | true                        | Turns the admin on or off                            |
| intentflow.admin.path                   | /intentflow-admin           | Where the UI is served; the API is under `<path>/api` |
| intentflow.admin.storage                | auto                        | `auto` (table with a `DataSource`, else memory), `jdbc` or `memory` |
| intentflow.admin.table                  | intentflow_catalog_override | Table for the overrides                              |
| intentflow.admin.create-table           | true                        | Create the table if it does not exist; `false` with Liquibase: include `db/intentflow/intentflow-admin.changelog.yaml` (table name parameter `intentflow.admin.table`) |
| intentflow.admin.refresh-interval       | 30s                         | How often an instance re-reads the table             |
| intentflow.admin.index-advisor          | true                        | Show the query plan and index suggestions            |
| intentflow.admin.security.enabled       | true                        | `false` opens the admin to everyone                  |
| intentflow.admin.security.authorities   | ROLE_ADMIN                  | Authorities of editors with Spring Security          |
| intentflow.admin.security.read-only-authorities | empty               | Authorities of read-only users with Spring Security  |
| intentflow.admin.security.login-url     | -                           | Login page the UI links to                           |
| intentflow.admin.security.username      | admin                       | HTTP Basic user without Spring Security              |
| intentflow.admin.security.password      | generated                   | HTTP Basic password without Spring Security          |

## Developing the UI

The UI lives in `src/main/ui` (React, TypeScript, Mantine, TanStack). Gradle builds it into the jar.
`./gradlew :intentflow-admin-spring-boot-starter:devUi` starts a live-reloading dev server on port 5174 that forwards
API calls to `INTENTFLOW_API_URL` (default `http://localhost:8080`).
