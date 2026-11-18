package com.example.samdi.global.config;

import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.export.SslMode;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.net.URI;

@org.springframework.context.annotation.Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        String url;
        String username;
        String password;
        String driver;
        if (isTest(environment) && !isMariaDbTest(environment)) {
            url = environment.getRequiredProperty("samdi.test-database-url");
            if (!url.equals("jdbc:h2:mem:samdi-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")) {
                throw new IllegalStateException("일반 테스트는 지정된 H2 메모리 DB만 사용할 수 있습니다.");
            }
            username = "sa";
            password = "";
            driver = "org.h2.Driver";
        } else if (isMariaDbTest(environment)) {
            url = environment.getRequiredProperty("SAMDI_TEST_MARIADB_URL");
            if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/game_db")) {
                throw new IllegalStateException("MariaDB 통합 테스트는 명시된 로컬 임시 DB만 사용할 수 있습니다.");
            }
            username = "root";
            password = "";
            driver = "org.mariadb.jdbc.Driver";
        } else {
            url = environment.getRequiredProperty("spring.datasource.url");
            if (environment.acceptsProfiles(Profiles.of("cloudtype"))) {
                validateCloudtype(url,
                        environment.getRequiredProperty("DB_HOST"),
                        environment.getRequiredProperty("DB_PORT"),
                        environment.getRequiredProperty("DB_NAME"));
            } else {
                validateTls(url);
            }
            username = environment.getRequiredProperty("spring.datasource.username");
            password = environment.getRequiredProperty("spring.datasource.password");
            driver = "org.mariadb.jdbc.Driver";
        }
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();
    }

    @Bean
    public HibernatePropertiesCustomizer schemaValidation(Environment environment) {
        return properties -> {
            properties.put("hibernate.hbm2ddl.auto",
                    isTest(environment) && !isMariaDbTest(environment) ? "create-drop" : "validate");
            properties.remove("jakarta.persistence.schema-generation.database.action");
            properties.remove("javax.persistence.schema-generation.database.action");
        };
    }

    private void validateTls(String url) {
        try {
            if (!url.startsWith("jdbc:mariadb://")) {
                throw new IllegalStateException("운영 DB는 MariaDB 연결 설정이 필요합니다.");
            }
            Configuration configuration = Configuration.parse(url);
            if (configuration.sslMode() != SslMode.VERIFY_FULL
                    || configuration.addresses().stream().anyMatch(address ->
                            address.sslMode != null && address.sslMode != SslMode.VERIFY_FULL)) {
                throw new IllegalStateException("운영 DB는 인증서와 호스트명을 검증하는 TLS 연결이 필요합니다.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("운영 DB의 TLS 연결 설정이 올바르지 않습니다.");
        }
    }

    private void validateCloudtype(String url, String host, String port, String database) {
        String normalizedHost = host.trim().replaceFirst("\\.$", "");
        if ("svc.sel3.cloudtype.app".equalsIgnoreCase(normalizedHost)) {
            throw new IllegalStateException("Cloudtype DB_HOST에는 내부 연결 주소를 사용해야 합니다.");
        }
        if (!url.matches("(?i).*[?&]sslMode=disable(?:&.*)?")) {
            throw new IllegalStateException("Cloudtype 내부 DB 연결은 sslMode=disable 설정이 필요합니다.");
        }
        try {
            if (!url.startsWith("jdbc:mariadb://")) {
                throw new IllegalStateException("Cloudtype DB는 MariaDB 연결 설정이 필요합니다.");
            }
            URI jdbcAddress = URI.create(url.substring("jdbc:".length()));
            if (!normalizedHost.equalsIgnoreCase(jdbcAddress.getHost())
                    || jdbcAddress.getPort() != Integer.parseInt(port)
                    || !("/" + database).equals(jdbcAddress.getPath())) {
                throw new IllegalStateException("Cloudtype DB URL은 내부 연결 환경변수와 일치해야 합니다.");
            }
            Configuration configuration = Configuration.parse(url);
            if (configuration.sslMode() != SslMode.DISABLE
                    || configuration.addresses().stream().anyMatch(address ->
                            address.sslMode != null && address.sslMode != SslMode.DISABLE)) {
                throw new IllegalStateException("Cloudtype 내부 DB 연결은 sslMode=disable 설정이 필요합니다.");
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new IllegalStateException("Cloudtype DB 연결 설정이 올바르지 않습니다.");
        }
    }

    private boolean isTest(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("test"));
    }

    private boolean isMariaDbTest(Environment environment) {
        return isTest(environment) && environment.getProperty("samdi.test.mariadb", Boolean.class, false);
    }
}
