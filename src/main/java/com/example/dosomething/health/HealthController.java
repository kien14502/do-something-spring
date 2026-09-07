package com.example.dosomething.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check công khai, dựng trên các health indicator của Actuator nên
 * PostgreSQL, Redis, Kafka và disk được kiểm tra sẵn mà không cần tự viết.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Kiểm tra tình trạng ứng dụng và các thành phần phụ thuộc")
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final String applicationName;
    private final String applicationVersion;

    public HealthController(HealthEndpoint healthEndpoint,
                            @Value("${spring.application.name:unknown}") String applicationName,
                            @Value("${app.version:unknown}") String applicationVersion) {
        this.healthEndpoint = healthEndpoint;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Health check",
            description = """
                    Trả về trạng thái tổng hợp của ứng dụng cùng trạng thái từng thành phần \
                    (PostgreSQL, Redis, Kafka, disk). Endpoint không yêu cầu xác thực để \
                    load balancer, Docker healthcheck và uptime monitor gọi được.

                    Mã HTTP phản ánh trạng thái: `200` khi UP, `503` khi bất kỳ thành phần \
                    nào không sẵn sàng.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ứng dụng và mọi thành phần đều UP",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HealthResponse.class))),
            @ApiResponse(responseCode = "503", description = "Có thành phần không sẵn sàng",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HealthResponse.class)))
    })
    public ResponseEntity<HealthResponse> health() {
        HealthDescriptor descriptor = healthEndpoint.health();
        Status status = descriptor.getStatus();
        HealthResponse body = new HealthResponse(
                status.getCode(), applicationName, applicationVersion, Instant.now(), components(descriptor));
        HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(body);
    }

    /**
     * Chỉ có component khi {@code management.endpoint.health.show-components} bật;
     * ngược lại Actuator trả về descriptor không kèm thành phần con.
     */
    private static Map<String, String> components(HealthDescriptor descriptor) {
        if (!(descriptor instanceof CompositeHealthDescriptor composite) || composite.getComponents() == null) {
            return Map.of();
        }
        Map<String, String> components = new TreeMap<>();
        composite.getComponents().forEach((name, component) -> components.put(name, component.getStatus().getCode()));
        return components;
    }
}
