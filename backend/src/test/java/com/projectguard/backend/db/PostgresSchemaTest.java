package com.projectguard.backend.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영과 같은 설정(빈 PostgreSQL + Flyway + ddl-auto=validate)으로 서버가 실제로 뜨는지 검사한다.
 * 다른 테스트는 H2에서 Hibernate가 테이블을 만들어 주므로, 마이그레이션 스크립트가 빠지거나 엔티티와
 * 어긋나도 통과해 버린다 — 실제로 스크립트 없이 validate로 띄우면 "missing table"로 시작조차 못 했다.
 */
@SpringBootTest(properties = {
        "FLYWAY_ENABLED=true",
        "DDL_AUTO=validate",
})
class PostgresSchemaTest {

    private static EmbeddedPostgres postgres;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws IOException {
        postgres = EmbeddedPostgres.start();
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        postgres.close();
    }

    @Test
    void 빈_PostgreSQL에_마이그레이션을_적용하면_엔티티_검증을_통과한다() {
        // 컨텍스트가 떴다는 것 자체가 validate 통과다. 스크립트가 실제로 적용됐는지도 확인한다.
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isPositive();
    }

    @Test
    void 변호사_서류는_대용량_객체가_아니라_bytea로_저장된다() {
        String type = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_name = 'lawyer_credential_documents' and column_name = 'data'",
                String.class);
        assertThat(type).isEqualTo("bytea");
    }
}
