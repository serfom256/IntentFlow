package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.IntentFlowAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSecurityTest {

    @EnableWebMvc
    static class Mvc {
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class,
                    IntentFlowAdminAutoConfiguration.class))
            .withUserConfiguration(Mvc.class)
            .withPropertyValues("intentflow.catalog.packages=io.github.serfom256.intentflow.admin")
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books | take 1"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            });

    private final WebApplicationContextRunner withoutSpringSecurity = runner
            .withClassLoader(new FilteredClassLoader("org.springframework.security"));

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void withoutSpringSecurityTheAdminAsksForHttpBasicCredentials() {
        withoutSpringSecurity.withPropertyValues("intentflow.admin.security.password=s3cret").run(ctx -> {
            MockMvc mvc = mvc(ctx);
            mvc.perform(get("/intentflow-admin/api/services"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                    .andExpect(jsonPath("$.kind").value("unauthenticated"));
            mvc.perform(get("/intentflow-admin/index.html")).andExpect(status().isUnauthorized());
            mvc.perform(get("/intentflow-admin/api/services").header("Authorization", basic("admin", "wrong")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/intentflow-admin/api/session").header("Authorization", basic("admin", "s3cret")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user").value("admin"));
            mvc.perform(get("/intentflow-admin/index.html").header("Authorization", basic("admin", "s3cret")))
                    .andExpect(status().isOk());
        });
    }

    @Test
    void withoutAConfiguredPasswordOneIsGeneratedForDevelopment() {
        withoutSpringSecurity.run(ctx -> {
            AdminAccess access = ctx.getBean(AdminAccess.class);
            assertThat(access).isInstanceOf(BasicAdminAccess.class);
            String password = ((BasicAdminAccess) access).password();
            assertThat(password).hasSizeGreaterThanOrEqualTo(20);
            mvc(ctx).perform(get("/intentflow-admin/api/services").header("Authorization", basic("admin", password)))
                    .andExpect(status().isOk());
        });
    }

    @Test
    void withSpringSecurityTheApplicationsAuthenticationDecides() {
        runner.withPropertyValues("intentflow.admin.security.login-url=/login").run(ctx -> {
            MockMvc mvc = mvc(ctx);
            mvc.perform(get("/intentflow-admin/api/services"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.login").value("/login"));
            mvc.perform(get("/intentflow-admin/index.html")).andExpect(status().isOk());

            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("bob", "x", "ROLE_USER"));
            mvc.perform(get("/intentflow-admin/api/services"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.kind").value("forbidden"))
                    .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMIN"));

            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("ann", "x", "ROLE_ADMIN"));
            mvc.perform(get("/intentflow-admin/api/services")).andExpect(status().isOk());
            mvc.perform(get("/intentflow-admin/api/session")
                    .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "t0ken")))
                    .andExpect(jsonPath("$.user").value("ann"))
                    .andExpect(jsonPath("$.csrf.header").value("X-CSRF-TOKEN"))
                    .andExpect(jsonPath("$.csrf.token").value("t0ken"));
        });
    }

    @Test
    void requiredAuthoritiesAreConfigurable() {
        runner.withPropertyValues("intentflow.admin.security.authorities=SCOPE_intentflow.admin").run(ctx -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("svc", "x", "SCOPE_intentflow.admin"));
            mvc(ctx).perform(get("/intentflow-admin/api/services")).andExpect(status().isOk());
        });
    }

    @Test
    void aCustomAdminAccessBeanReplacesTheDefaults() {
        runner.withBean(AdminAccess.class, () -> request -> "10.0.0.1".equals(request.getRemoteAddr())
                ? AdminAccess.Decision.allowed("ops")
                : AdminAccess.Decision.forbidden(null))
                .run(ctx -> {
                    MockMvc mvc = mvc(ctx);
                    mvc.perform(get("/intentflow-admin/api/services")).andExpect(status().isForbidden());
                    mvc.perform(get("/intentflow-admin/api/session").with(r -> {
                        r.setRemoteAddr("10.0.0.1");
                        return r;
                    })).andExpect(jsonPath("$.user").value("ops"));
                });
    }

    @Test
    void securityCanBeSwitchedOffExplicitly() {
        runner.withPropertyValues("intentflow.admin.security.enabled=false")
                .run(ctx -> mvc(ctx).perform(get("/intentflow-admin/api/services")).andExpect(status().isOk()));
    }

    @Test
    void readOnlyUsersBrowseAndExplainButCannotChangeOverrides() {
        runner.withPropertyValues("intentflow.admin.security.read-only-authorities=ROLE_CATALOG_VIEWER",
                "intentflow.dialect=postgresql").run(ctx -> {
                    MockMvc mvc = mvc(ctx);
                    SecurityContextHolder.getContext().setAuthentication(
                            new TestingAuthenticationToken("vic", "x", "ROLE_CATALOG_VIEWER"));

                    mvc.perform(get("/intentflow-admin/api/session"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.user").value("vic"))
                            .andExpect(jsonPath("$.editor").value(false));
                    mvc.perform(get("/intentflow-admin/api/services")).andExpect(status().isOk());
                    mvc.perform(post("/intentflow-admin/api/explain").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"books\"}")).andExpect(status().isOk());
                    mvc.perform(put("/intentflow-admin/api/services/books").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\": \"Every book\"}"))
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.kind").value("read-only"));
                    mvc.perform(delete("/intentflow-admin/api/services/books"))
                            .andExpect(status().isForbidden());
                });
    }

    @Test
    void editorsCanChangeOverrides() {
        runner.withPropertyValues("intentflow.admin.security.read-only-authorities=ROLE_CATALOG_VIEWER").run(ctx -> {
            MockMvc mvc = mvc(ctx);
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("eda", "x", "ROLE_ADMIN"));

            mvc.perform(get("/intentflow-admin/api/session")).andExpect(jsonPath("$.editor").value(true));
            mvc.perform(put("/intentflow-admin/api/services/books").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"description\": \"Every book\"}")).andExpect(status().isOk());
        });
    }

    @Test
    void usersWithNeitherRoleAreToldWhichAuthoritiesGrantAccess() {
        runner.withPropertyValues("intentflow.admin.security.read-only-authorities=ROLE_CATALOG_VIEWER").run(ctx -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("bob", "x", "ROLE_USER"));

            mvc(ctx).perform(get("/intentflow-admin/api/services"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMIN"))
                    .andExpect(jsonPath("$.authorities[1]").value("ROLE_CATALOG_VIEWER"));
        });
    }

    @Test
    void anOidcLoginWithTheGuidesRoleMapperGetsItsRole() {
        GrantedAuthoritiesMapper roles = OidcRoles.mapper("roles");
        runner.withPropertyValues("intentflow.admin.security.authorities=ROLE_intentflow-editor",
                "intentflow.admin.security.read-only-authorities=ROLE_intentflow-viewer").run(ctx -> {
                    MockMvc mvc = mvc(ctx);
                    SecurityContextHolder.getContext().setAuthentication(oidc(roles, "lena", "intentflow-viewer"));
                    mvc.perform(get("/intentflow-admin/api/session"))
                            .andExpect(jsonPath("$.user").value("lena"))
                            .andExpect(jsonPath("$.editor").value(false));

                    SecurityContextHolder.getContext().setAuthentication(oidc(roles, "omar", "intentflow-editor"));
                    mvc.perform(get("/intentflow-admin/api/session")).andExpect(jsonPath("$.editor").value(true));
                });
    }

    private static OAuth2AuthenticationToken oidc(GrantedAuthoritiesMapper roles, String user, String role) {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token").subject(user).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).claim("preferred_username", user)
                .claim("roles", List.of(role)).build();
        Collection<? extends GrantedAuthority> authorities = roles
                .mapAuthorities(List.of(new OidcUserAuthority(token)));
        DefaultOidcUser principal = new DefaultOidcUser(authorities, token, "preferred_username");
        return new OAuth2AuthenticationToken(principal, authorities, "corporate-sso");
    }
}
