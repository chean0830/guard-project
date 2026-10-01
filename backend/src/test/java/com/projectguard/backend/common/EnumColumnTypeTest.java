package com.projectguard.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Enumerated(STRING) 컬럼이 H2에서 ENUM 타입으로 만들어지면 허용 값이 그때의 enum 값으로 굳어, 나중에 enum에 값을 더하면
 * 저장·조회가 "Value not permitted"(500)로 실패한다 (결제 상태에 CANCELED·REFUNDED를 더한 뒤 관리자 결제 탭에서 실제로 발생).
 * 모든 enum 컬럼은 columnDefinition = "varchar(30)"으로 둬야 한다 — 새 enum 컬럼을 추가할 때 빠뜨리면 이 테스트가 잡는다.
 */
@SpringBootTest
class EnumColumnTypeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enum_컬럼은_DB_ENUM_타입으로_만들어지지_않는다() {
        var enumColumns = jdbc.queryForList(
                "SELECT TABLE_NAME || '.' || COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND DATA_TYPE = 'ENUM'",
                String.class);
        assertThat(enumColumns).isEmpty();
    }
}
