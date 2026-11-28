package com.dormrepair.v2.preparation;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
@Repository @ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PreparationRepository {private final JdbcTemplate jdbc;public PreparationRepository(JdbcTemplate j){jdbc=j;}public JdbcTemplate jdbc(){return jdbc;}public void lock(){jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);}}
