package com.example.dosomething.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dosomething.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dùng HealthEndpoint thật với các indicator giả để kiểm tra cả phần tổng hợp
 * trạng thái của Actuator, không chỉ riêng controller.
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, HealthControllerTest.StubHealthIndicators.class})
@ImportAutoConfiguration({HealthContributorRegistryAutoConfiguration.class, HealthEndpointAutoConfiguration.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("db")
    private StubHealthIndicator db;

    @Autowired
    @Qualifier("redis")
    private StubHealthIndicator redis;

    @BeforeEach
    void resetIndicators() {
        db.setStatus(Status.UP);
        redis.setStatus(Status.UP);
    }

    @Test
    void returnsOkAndComponentsWhenEverythingIsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("do-something"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.components.db").value("UP"))
                .andExpect(jsonPath("$.components.redis").value("UP"));
    }

    @Test
    void returnsServiceUnavailableWhenAComponentIsDown() throws Exception {
        db.setStatus(Status.DOWN);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db").value("DOWN"))
                .andExpect(jsonPath("$.components.redis").value("UP"));
    }

    /** Health check là ngoại lệ; mọi endpoint khác vẫn phải xác thực. */
    @Test
    void requiresAuthenticationOutsideThePublicPaths() throws Exception {
        mockMvc.perform(get("/api/anything-else"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubHealthIndicators {

        @Bean
        StubHealthIndicator db() {
            return new StubHealthIndicator();
        }

        @Bean
        StubHealthIndicator redis() {
            return new StubHealthIndicator();
        }
    }

    static class StubHealthIndicator implements HealthIndicator {

        private volatile Status status = Status.UP;

        void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public Health health() {
            return Health.status(status).build();
        }
    }
}
