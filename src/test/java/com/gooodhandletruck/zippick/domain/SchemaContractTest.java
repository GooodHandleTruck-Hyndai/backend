package com.gooodhandletruck.zippick.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.gooodhandletruck.zippick.global.config.JpaConfig;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * 테이블 구조 고정(계약) 테스트. (#4 테스트 보강에서 추가)
 *
 * 엔티티에서 칸 이름·NULL 허용·길이·UNIQUE·인덱스·FK 가 바뀌면 여기서 바로 깨져요.
 * 적재 로직(지문+순번, 건물 구분 키)과 ERD 가 이 구조에 기대고 있어서, 바꿀 때는 ERD 와 이 표를 같이 고쳐야 해요.
 * DB 종류에 상관없게 JDBC 메타데이터로 읽고, 이름은 대소문자를 무시하고 비교해요 (H2 는 대문자로 저장).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 팀 테스트 설정(H2 MODE=MySQL) 그대로
@Import(JpaConfig.class)
class SchemaContractTest {

    @Autowired
    private DataSource dataSource;

    @ParameterizedTest(name = "{0}.{1} nullable={2} length={3}")
    @CsvSource({
            // 테이블, 칸, NULL 허용, 최대 길이(-1 = 길이 검사 안 함: 숫자·날짜·enum)
            "buildings, id, false, -1",
            "buildings, created_at, true, -1",
            "buildings, updated_at, true, -1",
            "buildings, building_type, false, -1",
            "buildings, sgg_cd, false, 5",
            "buildings, umd_nm, false, 20",
            "buildings, jibun, false, 20",
            "buildings, apt_nm, false, 100",
            "buildings, build_year, true, -1",
            "buildings, adm_cd, true, 10",
            "buildings, road_addr, true, 200",
            "buildings, latitude, true, -1",
            "buildings, longitude, true, -1",
            "buildings, coord_source, true, -1",
            "trades, id, false, -1",
            "trades, created_at, true, -1",
            "trades, updated_at, true, -1",
            "trades, building_id, false, -1",
            "trades, fingerprint, false, 64",
            "trades, seq, false, -1",
            "trades, sgg_cd, false, 5",
            "trades, deal_ym, false, 6",
            "trades, deal_date, false, -1",
            "trades, deal_amount, false, -1",
            "trades, exclu_use_ar, false, -1",
            "trades, floor, false, -1",
            "trades, apt_dong, true, 20",
            "trades, cdeal_type, true, 1",
            "trades, cdeal_day, true, -1",
            "trades, rgst_date, true, -1",
            "trades, dealing_gbn, true, 10",
            "trades, estate_agent_sgg_nm, true, 200",
            "trades, sler_gbn, true, 10",
            "trades, buyer_gbn, true, 10",
            "trades, land_leasehold_gbn, true, 1",
            "legal_dongs, adm_cd, false, 10",
            "legal_dongs, sgg_cd, false, 5",
            "legal_dongs, sido_nm, false, 20",
            "legal_dongs, sgg_nm, false, 20",
            "legal_dongs, umd_nm, false, 20"
    })
    void 칸_이름_NULL허용_길이가_ERD와_같다(String table, String column, boolean nullable, int length)
            throws SQLException {
        Map<String, ColumnInfo> columns = columns(table);

        assertThat(columns).containsKey(norm(column));
        ColumnInfo info = columns.get(norm(column));
        assertThat(info.nullable()).as("NULL 허용").isEqualTo(nullable);
        if (length > 0) {
            assertThat(info.size()).as("최대 길이").isEqualTo(length);
        }
    }

    @ParameterizedTest(name = "{0} 칸 수 = {1}")
    @CsvSource({"buildings, 14", "trades, 21", "legal_dongs, 5"})
    void 테이블_칸_수가_ERD와_같다(String table, int count) throws SQLException {
        // 칸을 추가·삭제하면 ERD 와 이 테스트를 같이 고치라는 신호예요
        assertThat(columns(table)).hasSize(count);
    }

    @ParameterizedTest(name = "{0}.exclu_use_ar = DECIMAL({1},{2})")
    @CsvSource({"trades, 9, 4"}) // #8: 국토부 원본이 소수 4자리까지 와서 반올림 없이 저장
    void 전용면적은_소수_4자리까지_저장한다(String table, int precision, int scale) throws SQLException {
        ColumnInfo info = columns(table).get(norm("exclu_use_ar"));

        assertThat(info.size()).isEqualTo(precision);
        assertThat(info.decimalDigits()).isEqualTo(scale);
    }

    @ParameterizedTest(name = "{0} UNIQUE {1} = ({2})")
    @CsvSource(delimiter = '|', value = {
            "buildings | uk_buildings_key | building_type,sgg_cd,umd_nm,jibun,apt_nm",
            "trades | uk_trades_fp_seq | fingerprint,seq"
    })
    void 고유_키가_정해진_칸_순서로_걸려_있다(String table, String name, String expectedColumns) throws SQLException {
        assertThat(indexColumns(table, name, true)).containsExactly(split(expectedColumns));
    }

    @ParameterizedTest(name = "{0} INDEX {1} = ({2})")
    @CsvSource(delimiter = '|', value = {
            "buildings | idx_buildings_lat_lng | latitude,longitude",
            "trades | idx_trades_building_date | building_id,deal_date",
            "trades | idx_trades_sgg_ym | sgg_cd,deal_ym",
            "legal_dongs | idx_legal_dongs_sgg_umd | sgg_cd,umd_nm"
    })
    void 조회용_인덱스가_있다(String table, String name, String expectedColumns) throws SQLException {
        assertThat(indexColumns(table, name, false)).containsExactly(split(expectedColumns));
    }

    @ParameterizedTest(name = "{0}.{1} → {2}")
    @CsvSource({"trades, building_id, buildings", "buildings, adm_cd, legal_dongs"})
    void 외래키가_걸려_있다(String table, String column, String referenced) throws SQLException {
        Map<String, String> fks = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
                ResultSet rs = c.getMetaData().getImportedKeys(null, null, tableName(c, table))) {
            while (rs.next()) {
                fks.put(norm(rs.getString("FKCOLUMN_NAME")), norm(rs.getString("PKTABLE_NAME")));
            }
        }
        assertThat(fks).containsEntry(norm(column), norm(referenced));
    }

    // ---- 메타데이터 읽기 도우미 ----

    private record ColumnInfo(boolean nullable, int size, int decimalDigits) {
    }

    private Map<String, ColumnInfo> columns(String table) throws SQLException {
        Map<String, ColumnInfo> result = new TreeMap<>();
        try (Connection c = dataSource.getConnection();
                ResultSet rs = c.getMetaData().getColumns(null, null, tableName(c, table), null)) {
            while (rs.next()) {
                result.put(norm(rs.getString("COLUMN_NAME")), new ColumnInfo(
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS")));
            }
        }
        return result;
    }

    /** 인덱스 이름이 DB 마다 접미사가 붙을 수 있어서(H2: UK_..._INDEX_X), 이름이 그걸로 시작하는 인덱스를 찾아요 */
    private List<String> indexColumns(String table, String indexName, boolean unique) throws SQLException {
        Map<String, Map<Short, String>> indexes = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
                ResultSet rs = c.getMetaData().getIndexInfo(null, null, tableName(c, table), unique, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name == null || !norm(name).startsWith(norm(indexName))) {
                    continue;
                }
                indexes.computeIfAbsent(name, k -> new TreeMap<>())
                        .put(rs.getShort("ORDINAL_POSITION"), norm(rs.getString("COLUMN_NAME")));
            }
        }
        assertThat(indexes).as(indexName + " 인덱스").hasSize(1);
        return new ArrayList<>(indexes.values().iterator().next().values());
    }

    private String tableName(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name.equalsIgnoreCase(table)) {
                    return name;
                }
            }
        }
        throw new AssertionError("테이블이 없어요: " + table);
    }

    private static String norm(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String[] split(String columns) {
        return columns.split(",");
    }
}
