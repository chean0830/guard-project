package com.projectguard.backend.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영과 같은 설정(빈 MySQL + Flyway + ddl-auto=validate)으로 서버가 실제로 뜨는지 검사한다.
 * 다른 테스트는 H2에서 Hibernate가 테이블을 만들어 주므로, 마이그레이션 스크립트가 빠지거나 엔티티와
 * 어긋나도 통과해 버린다 — 실제로 스크립트 없이 validate로 띄우면 "missing table"로 시작조차 못 했다.
 *
 * MySQL이 있어야 해서 MYSQL_TEST_URL이 있을 때만 돈다(CI는 MySQL 컨테이너로 항상 실행).
 * 시작할 때 그 DB를 지우고 다시 만드므로 반드시 테스트 전용 DB를 가리켜야 한다.
 */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "FLYWAY_ENABLED=true",
        "DDL_AUTO=validate",
})
class MySqlSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws SQLException {
        String url = System.getenv("MYSQL_TEST_URL");
        String username = System.getenv("MYSQL_TEST_USERNAME");
        String password = System.getenv("MYSQL_TEST_PASSWORD");
        recreateDatabase(url, username, password);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    private static void recreateDatabase(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            String database = connection.getCatalog();
            statement.execute("drop database if exists `" + database + "`");
            statement.execute("create database `" + database + "` character set utf8mb4");
        }
    }

    @Test
    void 빈_MySQL에_마이그레이션을_적용하면_엔티티_검증을_통과한다() {
        // 컨텍스트가 떴다는 것 자체가 validate 통과다. 스크립트가 실제로 적용됐는지도 확인한다.
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isPositive();
    }

    @Test
    void 변호사_서류는_업로드_한도_20MB를_담을_수_있는_longblob이다() {
        String type = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns where table_schema = database() "
                        + "and table_name = 'lawyer_credential_documents' and column_name = 'data'",
                String.class);
        assertThat(type).isEqualTo("longblob");
    }
}
