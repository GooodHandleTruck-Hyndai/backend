package com.gooodhandletruck.sidong.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버가 살아 있는지 확인하는 API.
 * 차량 앱이 시작할 때 이걸 호출해서 "서버: 연결됨" 을 띄움
 *
 * 앱과 약속한 형식이라 공통 응답(ApiResponse)으로 감싸지 않음
 */
@Tag(name = "Health", description = "서버 상태 확인")
@RestController
public class HealthController {

    @Operation(summary = "서버 상태 확인", description = "정상이면 {\"status\": \"ok\"} 를 반환")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
