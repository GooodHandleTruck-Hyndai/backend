package com.gooodhandletruck.zippick.domain.region.repository;

import com.gooodhandletruck.zippick.domain.region.entity.LegalDong;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법정동코드표 조회 (#4 에서 추가) */
public interface LegalDongRepository extends JpaRepository<LegalDong, String> {
}
