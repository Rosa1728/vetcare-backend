package pe.edu.utec.vetcare.integration;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Base reutilizable: nunca usa la configuración de la base habitual. */
@SpringBootTest(properties = {"spring.config.location=classpath:/application-integration.properties", "spring.devtools.restart.enabled=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgresIntegrationSupport {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("vetcare_p0_integration")
            .withUsername("vetcare_test")
            .withPassword(UUID.randomUUID().toString())
            .withReuse(false);

    private static final String TEST_SIGNING_KEY = signingKey();

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) {
        // Las URL de Hibernate y Flyway provienen obligatoriamente del contenedor.
        // Si Docker falla, no existe una conexión alternativa a localhost:5432.
        registry.add("spring.datasource.url", PostgresIntegrationSupport::isolatedJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", PostgresIntegrationSupport::isolatedJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> TEST_SIGNING_KEY);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.clean-disabled", () -> true);
        registry.add("VETCARE_BOOTSTRAP_ADMIN_ENABLED", () -> false);
        registry.add("vetcare.correo.habilitado", () -> false);
        registry.add("vetcare.correo.cron", () -> "-");
        registry.add("vetcare.recordatorios.cron", () -> "-");
    }

    private static String isolatedJdbcUrl() {
        if (!POSTGRES.isRunning() || !"vetcare_p0_integration".equals(POSTGRES.getDatabaseName()))
            throw new IllegalStateException("La integración requiere el PostgreSQL temporal esperado");
        String url = POSTGRES.getJdbcUrl();
        if (!url.contains("/vetcare_p0_integration") || url.contains("vetcare_mejoras"))
            throw new IllegalStateException("Datasource de integración no autorizado");
        return url;
    }

    private static String signingKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
