package com.gooodhandletruck.zippick.domain.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관심 건물 알림 목록 (최신순)")
public record AlertListResponse(List<AlertResponse> alerts) {
}
