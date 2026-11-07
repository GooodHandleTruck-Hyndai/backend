package com.gooodhandletruck.zippick.domain.region.repository;

import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법정동코드표 조회 (#4 에서 추가, #6 에서 구 코드 조회 추가) */
public interface LegalDongRepository extends JpaRepository<LegalDong, String> {

    /** 구 코드로 시도·시군구 이름을 찾아요 (같은 구의 법정동은 시도·시군구 이름이 같아서 아무 행이나 하나) */
    Optional<LegalDong> findFirstBySggCd(String sggCd);
}
