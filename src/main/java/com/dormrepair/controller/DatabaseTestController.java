
package com.dormrepair.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseTestController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/db-test")
    public String testDatabase() {
        String database = jdbcTemplate.queryForObject(
                "select current_database()",
                String.class
        );

        return "Connected to PostgreSQL: " + database;
    }
}
