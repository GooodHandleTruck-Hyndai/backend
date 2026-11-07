package com.gooodhandletruck.zippick.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 법정동코드표 (legal_dongs).
 *
 * 위치 → 구 코드 변환과, 국토부 실거래(sggCd + umdNm)를 행안부 주소(admCd)와 이어 줄 때 써요.
 * 데이터는 행안부 법정동코드 파일로 한 번 채워요. (#4 에서 테이블만 추가)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "legal_dongs", indexes = @Index(name = "idx_legal_dongs_sgg_umd", columnList = "sgg_cd, umd_nm"))
public class LegalDong {

    /** 법정동코드 10자리 (예: 1144010100 = 마포구 아현동). 공식 코드라 자동 증가가 아니에요 */
    @Id
    @Column(name = "adm_cd", length = 10)
    private String admCd;

    /** adm_cd 앞 5자리 = 국토부 LAWD_CD */
    @Column(name = "sgg_cd", nullable = false, length = 5)
    private String sggCd;

    @Column(name = "sido_nm", nullable = false, length = 20)
    private String sidoNm;

    @Column(name = "sgg_nm", nullable = false, length = 20)
    private String sggNm;

    @Column(name = "umd_nm", nullable = false, length = 20)
    private String umdNm;

    @Builder
    private LegalDong(String admCd, String sggCd, String sidoNm, String sggNm, String umdNm) {
        this.admCd = admCd;
        this.sggCd = sggCd;
        this.sidoNm = sidoNm;
        this.sggNm = sggNm;
        this.umdNm = umdNm;
    }
}
