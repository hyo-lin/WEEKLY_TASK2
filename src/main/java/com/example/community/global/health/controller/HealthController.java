package com.example.community.global.health.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.Connection;


@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health/check")
    public ResponseEntity<CommonResponse<Void>> health() {
        try (Connection connection = dataSource.getConnection()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(CommonResponse.success(StatusCode.HEALTH_CHECK_SUCCESS, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
