package com.example.dosomething.health;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * Kết quả health check trả về cho client.
 *
 * @param status      trạng thái tổng hợp, gộp từ tất cả thành phần
 * @param application tên ứng dụng
 * @param version     phiên bản build
 * @param timestamp   thời điểm kiểm tra
 * @param components  trạng thái từng thành phần, rỗng nếu chưa có health indicator nào
 */
@Schema(name = "HealthResponse", description = "Tình trạng ứng dụng và các thành phần phụ thuộc")
public record HealthResponse(

        @Schema(description = "Trạng thái tổng hợp của ứng dụng",
                example = "UP",
                allowableValues = {"UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN"})
        String status,

        @Schema(description = "Tên ứng dụng", example = "do-something")
        String application,

        @Schema(description = "Phiên bản build", example = "0.0.1-SNAPSHOT")
        String version,

        @Schema(description = "Thời điểm kiểm tra, theo UTC", example = "2026-09-07T03:21:00Z")
        Instant timestamp,

        @Schema(description = "Trạng thái từng thành phần: db, redis, kafka, diskSpace, ping...",
                example = "{\"db\":\"UP\",\"redis\":\"UP\",\"kafka\":\"UP\"}")
        Map<String, String> components) {
}
