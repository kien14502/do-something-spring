package com.example.dosomething.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata cho tài liệu OpenAPI. springdoc quét controller để sinh phần paths;
 * bean này chỉ bổ sung thông tin chung của tài liệu.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI doSomethingOpenApi(@Value("${spring.application.name:unknown}") String applicationName,
                               @Value("${app.version:unknown}") String applicationVersion) {
        return new OpenAPI().info(new Info()
                .title(applicationName + " API")
                .version(applicationVersion)
                .description("""
                        REST API của dịch vụ do-something.

                        Hầu hết endpoint yêu cầu HTTP Basic; riêng `GET /api/health` là public \
                        để load balancer và Docker healthcheck gọi được.""")
                .license(new License().name("Proprietary")));
    }
}
