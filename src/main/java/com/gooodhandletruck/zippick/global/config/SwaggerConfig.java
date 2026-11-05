package com.gooodhandletruck.zippick.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 설정.
 * 서버 실행 후 http://localhost:8080/swagger-ui.html 에서 확인해요.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI zippickOpenApi() {
        return new OpenAPI().info(new Info()
                .title("집픽 API")
                .description("GooodHandleTruck · 차 안에서 내 주변 건물 시세를 확인하는 플레오스 앱")
                .version("v0.0.1"));
    }
}
