package com.example.dosomething.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dosomething.config.OpenApiConfig;
import com.example.dosomething.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Tài liệu OpenAPI phải mô tả được health check và truy cập được không cần đăng nhập. */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, OpenApiConfig.class})
@ImportAutoConfiguration({
        HealthContributorRegistryAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class,
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesHealthEndpointInTheOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("do-something API"))
                .andExpect(jsonPath("$.paths['/api/health'].get.tags[0]").value("Health"))
                .andExpect(jsonPath("$.paths['/api/health'].get.summary").value("Health check"))
                .andExpect(jsonPath("$.paths['/api/health'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/health'].get.responses['503']").exists())
                .andExpect(jsonPath("$.components.schemas.HealthResponse.properties.status.example").value("UP"));
    }
}
