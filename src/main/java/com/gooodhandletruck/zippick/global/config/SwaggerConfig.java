package com.gooodhandletruck.zippick.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 설정.
 * 서버 실행 후 http://localhost:8080/swagger-ui.html 에서 확인해요.
 *
 * 오른쪽 위 Authorize 버튼에 익명 ID 를 한 번 넣으면,
 * 모든 API 호출에 X-Anonymous-Id 헤더가 자동으로 붙어요.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI zippickOpenApi() {
        SecurityScheme anonymousIdScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(ApiHeaders.ANONYMOUS_ID)
                .description("POST /api/v1/devices 로 받은 익명 ID");

        return new OpenAPI()
                .info(new Info()
                        .title("집픽 API")
                        .description("""
                                GooodHandleTruck · 차 안에서 내 주변 건물 시세를 확인하는 플레오스 앱

                                - 1) POST /api/v1/devices 로 익명 ID 를 받아요.
                                - 2) 오른쪽 위 Authorize 에 익명 ID 를 넣어요.
                                - 3) 나머지 API 를 호출해요.
                                """)
                        .version("v0.1"))
                .components(new Components().addSecuritySchemes(ApiHeaders.ANONYMOUS_ID_SCHEME, anonymousIdScheme))
                .addSecurityItem(new SecurityRequirement().addList(ApiHeaders.ANONYMOUS_ID_SCHEME));
    }
}
