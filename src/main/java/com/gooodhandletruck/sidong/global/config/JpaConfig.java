package com.gooodhandletruck.sidong.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseTimeEntity 의 생성/수정 시간을 자동으로 채워주는 설정 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
