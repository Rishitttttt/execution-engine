package com.ocee.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public HealthController(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() { return Map.of("status", "ok"); }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, String>> readyz() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "db_unavailable"));
        }
        try {
            redis.getConnectionFactory().getConnection().ping();
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "redis_unavailable"));
        }
        return ResponseEntity.ok(Map.of("status", "ready"));
    }
}
