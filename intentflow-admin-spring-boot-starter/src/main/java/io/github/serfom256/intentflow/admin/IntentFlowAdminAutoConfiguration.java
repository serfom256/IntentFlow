package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

@Slf4j
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration" })
@ConditionalOnProperty(prefix = "intentflow.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IntentFlowAdminProperties.class)
public class IntentFlowAdminAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnExpression("!'${intentflow.admin.storage:auto}'.equalsIgnoreCase('memory')")
    static class JdbcStorage {

        @Bean
        @ConditionalOnMissingBean
        @DependsOnDatabaseInitialization
        CatalogOverrides intentFlowCatalogOverrides(DataSource dataSource, IntentFlowAdminProperties properties) {
            log.info("IntentFlow admin keeps catalog overrides in table {}", properties.table());
            return new JdbcCatalogOverrides(dataSource, properties.table(), properties.createTable(),
                    properties.refreshInterval());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    CatalogOverrides intentFlowInMemoryCatalogOverrides(IntentFlowAdminProperties properties) {
        if (properties.storage() == IntentFlowAdminProperties.Storage.JDBC) {
            throw new IllegalStateException("intentflow.admin.storage=jdbc needs a DataSource");
        }
        log.warn("IntentFlow admin keeps catalog overrides in memory; they are lost on restart");
        return ServiceCatalogs.inMemoryOverrides();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnProperty(prefix = "intentflow.admin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SpringSecurityAccess {

        @Bean
        @ConditionalOnMissingBean
        AdminAccess intentFlowAdminAccess(IntentFlowAdminProperties properties) {
            IntentFlowAdminProperties.Security security = properties.security();
            log.info("IntentFlow admin uses the application's Spring Security: editors need one of {}, "
                    + "read-only users one of {}", security.authorities(), security.readOnlyAuthorities());
            return new SecurityContextAdminAccess(security.authorities(), security.readOnlyAuthorities());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    AdminAccess intentFlowFallbackAdminAccess(IntentFlowAdminProperties properties) {
        IntentFlowAdminProperties.Security security = properties.security();
        if (!security.enabled()) {
            log.warn("IntentFlow admin at {}/ is open to everyone: intentflow.admin.security.enabled=false",
                    properties.path());
            return request -> AdminAccess.Decision.allowed(null);
        }
        String password = security.password();
        if (password == null || password.isBlank()) {
            password = BasicAdminAccess.generatePassword();
            log.warn("""

                    Using generated IntentFlow admin password: {}
                    Sign in at {}/ as '{}'. This password is for development only; set
                    intentflow.admin.security.password or add Spring Security to the application.
                    """, password, properties.path(), security.username());
        }
        return new BasicAdminAccess(security.username(), password);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(DispatcherServlet.class)
    static class Web implements WebMvcConfigurer {

        private final IntentFlowAdminProperties properties;
        private final AdminAccess access;

        Web(IntentFlowAdminProperties properties, AdminAccess access) {
            this.properties = properties;
            this.access = access;
        }

        @Bean
        CatalogAdmin intentFlowCatalogAdmin(ServiceCatalog catalog, ObjectProvider<IntentPipeline> pipeline,
                ObjectProvider<DataSource> dataSource, ObjectProvider<AuditLog> audit) {
            DataSource source = properties.indexAdvisor() ? dataSource.getIfUnique() : null;
            return new CatalogAdmin(catalog, pipeline.getObject(), source == null ? null : new IndexAdvisor(source),
                    audit.getIfAvailable(() -> AuditLog.NONE));
        }

        @Bean
        CatalogAdminController intentFlowCatalogAdminController(CatalogAdmin admin) {
            return new CatalogAdminController(admin);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            String path = properties.path();
            registry.addInterceptor(new AdminAccessInterceptor(access, path, properties.security().authorities(),
                    properties.security().readOnlyAuthorities(), properties.security().loginUrl()))
                    .addPathPatterns(path, path + "/**");
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler(properties.path() + "/**")
                    .addResourceLocations("classpath:/intentflow-admin-ui/");
        }

        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            String path = properties.path();
            registry.addRedirectViewController(path, path + "/");
            registry.addViewController(path + "/").setViewName("forward:" + path + "/index.html");
        }
    }
}
