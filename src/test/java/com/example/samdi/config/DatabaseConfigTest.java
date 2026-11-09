package com.example.samdi.config;

import com.example.samdi.SamdiApplication;
import com.example.samdi.global.config.DatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.HashMap;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    private final DatabaseConfig config = new DatabaseConfig();

    @Test
    void ordinaryTestsIgnoreExternalDatasourceAndHikariSettings() throws Exception {
        MockEnvironment environment = testEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mariadb://never-connect.invalid/game_db")
                .withProperty("spring.datasource.driver-class-name", "org.mariadb.jdbc.Driver")
                .withProperty("spring.datasource.username", "external-user")
                .withProperty("spring.datasource.password", "external-test-value")
                .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:mariadb://never-connect.invalid/game_db")
                .withProperty("spring.datasource.hikari.data-source-class-name", "org.mariadb.jdbc.MariaDbDataSource");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(environment)) {
            assertTrue(dataSource.getJdbcUrl().startsWith("jdbc:h2:mem:samdi-test;"));
            assertEquals("org.h2.Driver", dataSource.getDriverClassName());
            assertEquals("sa", dataSource.getUsername());
            assertEquals("", dataSource.getPassword());
            assertNull(dataSource.getDataSourceClassName());
            try (var connection = dataSource.getConnection()) {
                assertEquals("H2", connection.getMetaData().getDatabaseProductName());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mariadb://never-connect.invalid/game_db",
            "jdbc:h2:file:unsafe",
            "jdbc:h2:mem:unsafe;INIT=RUNSCRIPT FROM 'unsafe.sql'"
    })
    void unsafeTestDatabaseOverrideIsRejected(String url) {
        MockEnvironment environment = testEnvironment().withProperty("samdi.test-database-url", url);

        assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mariadb://never-connect.invalid/game_db",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=disable",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=trust",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=verify-ca",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=verify-full&sslMode=disable",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=verify-full&useSsl=true&trustServerCertificate=true",
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=verify-full&useSsl=true&disableSslHostnameVerification=true",
            "jdbc:mariadb://address=(host=never-connect.invalid)(sslMode=disable)/game_db?sslMode=verify-full",
            "jdbc:mariadb://address=(host=never-connect.invalid)(sslMode=trust)/game_db?sslMode=verify-full",
            "jdbc:mariadb://address=(host=never-connect.invalid)(sslMode=verify-ca)/game_db?sslMode=verify-full",
            "jdbc:mariadb://never-connect.invalid,address=(host=other.invalid)(sslMode=disable)/game_db?sslMode=verify-full",
            "jdbc:mysql://never-connect.invalid/game_db?sslMode=verify-full",
            ""
    })
    void unsafeProductionTlsIsRejectedBeforeDataSourceCreation(String url) {
        MockEnvironment environment = productionEnvironment().withProperty("spring.datasource.url", url);

        assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
    }

    @Test
    void missingProductionUrlIsRejectedBeforeDataSourceCreation() {
        assertThrows(IllegalStateException.class, () -> config.dataSource(productionEnvironment()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mariadb://never-connect.invalid/game_db?sslMode=verify-full",
            "jdbc:mariadb://address=(host=never-connect.invalid)(sslMode=verify-full)/game_db?sslMode=verify-full",
            "jdbc:mariadb://never-connect.invalid,address=(host=other.invalid)(sslMode=verify-full)/game_db?sslMode=verify-full"
    })
    void verifiedTlsConfigurationDoesNotOpenConnection(String url) {
        MockEnvironment environment = productionEnvironment().withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:mariadb://never-connect.invalid/insecure")
                .withProperty("spring.datasource.hikari.data-source-properties.sslMode", "disable");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(environment)) {
            assertEquals(url, dataSource.getJdbcUrl());
            assertTrue(dataSource.getDataSourceProperties().isEmpty());
            assertNull(dataSource.getHikariPoolMXBean());
        }
    }

    @Test
    void mariaDbIntegrationRequiresExplicitOptIn() {
        MockEnvironment environment = testEnvironment().withProperty("SAMDI_TEST_MARIADB_URL",
                "jdbc:mariadb://127.0.0.1:12345/game_db");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(environment)) {
            assertEquals("org.h2.Driver", dataSource.getDriverClassName());
        }
    }

    @Test
    void mariaDbIntegrationKeepsLocalDatasourceAndValidate() {
        MockEnvironment environment = testEnvironment().withProperty("samdi.test.mariadb", "true")
                .withProperty("SAMDI_TEST_MARIADB_URL", "jdbc:mariadb://127.0.0.1:12345/game_db");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(environment)) {
            assertEquals("jdbc:mariadb://127.0.0.1:12345/game_db", dataSource.getJdbcUrl());
            assertEquals("org.mariadb.jdbc.Driver", dataSource.getDriverClassName());
            assertNull(dataSource.getHikariPoolMXBean());
        }
        var properties = new HashMap<String, Object>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("jakarta.persistence.schema-generation.database.action", "drop-and-create");
        config.schemaValidation(environment).customize(properties);
        assertEquals("validate", properties.get("hibernate.hbm2ddl.auto"));
        assertFalse(properties.containsKey("jakarta.persistence.schema-generation.database.action"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mariadb://never-connect.invalid/game_db",
            "jdbc:mariadb://127.0.0.1:12345/game_db?anything=true",
            "jdbc:h2:mem:unexpected"
    })
    void mariaDbIntegrationRejectsNonDisposableUrlForms(String url) {
        MockEnvironment environment = testEnvironment().withProperty("samdi.test.mariadb", "true")
                .withProperty("SAMDI_TEST_MARIADB_URL", url);

        assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
    }

    @Test
    void productionSchemaSettingsCannotEnableDdl() {
        var properties = new HashMap<String, Object>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("jakarta.persistence.schema-generation.database.action", "drop-and-create");
        properties.put("javax.persistence.schema-generation.database.action", "create");

        config.schemaValidation(productionEnvironment()).customize(properties);

        assertEquals("validate", properties.get("hibernate.hbm2ddl.auto"));
        assertFalse(properties.containsKey("jakarta.persistence.schema-generation.database.action"));
        assertFalse(properties.containsKey("javax.persistence.schema-generation.database.action"));
    }

    @Test
    void productionApplicationStopsBeforeOpeningUnsafeDbConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(200);
            String url = "jdbc:mariadb://127.0.0.1:" + listener.getLocalPort()
                    + "/game_db?connectTimeout=1000&socketTimeout=1000";

            Exception failure = assertThrows(Exception.class, () ->
                    new SpringApplicationBuilder(SamdiApplication.class)
                            .registerShutdownHook(false)
                            .logStartupInfo(false)
                            .run("--spring.profiles.active=production-startup-check",
                                    "--spring.main.banner-mode=off",
                                    "--server.address=127.0.0.1",
                                    "--server.port=0",
                                    "--spring.datasource.url=" + url,
                                    "--spring.datasource.username=test-user",
                                    "--spring.datasource.password=test-only-value",
                                    "--jwt.secret=production-startup-check-only-key-20261026"));
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertInstanceOf(IllegalStateException.class, cause);
            assertTrue(cause.getMessage().contains("TLS"));
            assertThrows(SocketTimeoutException.class, listener::accept);
        }
    }

    private MockEnvironment testEnvironment() {
        MockEnvironment environment = new MockEnvironment().withProperty("samdi.test-database-url",
                "jdbc:h2:mem:samdi-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        environment.setActiveProfiles("test");
        return environment;
    }

    private MockEnvironment productionEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.username", "test-user")
                .withProperty("spring.datasource.password", "test-only-value");
    }
}
