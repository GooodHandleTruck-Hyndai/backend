package com.gooodhandletruck.sidong.global.config;

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
    public OpenAPI sidongOpenApi() {
        return new OpenAPI().info(new Info()
                .title("시동 API")
                .description("GooodHandleTruck · 주행 중 운전자를 돕는 차량용 서비스")
                .version("v0.0.1"));
    }
}
