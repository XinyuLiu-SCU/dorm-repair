package com.dormrepair.workflow;

import org.flowable.engine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.dormrepair.service.RepairOrderService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:postgresql://localhost:5432/dorm_repair_migration_test_20261123_d",
 "spring.datasource.username=${FLOWABLE_TEST_DB_USER:sota}", "spring.datasource.password=${FLOWABLE_TEST_DB_PASSWORD:}",
 "app.jwt.secret=migration-rehearsal-test-key-32-bytes-only",
 "app.workflow.enabled=true", "flowable.process.enabled=true", "flowable.database-schema-update=false", "flowable.history-level=audit"})
@EnabledIfSystemProperty(named="flowable.stageD",matches="true")
class MigrationRehearsalTest {
 @Autowired ProcessEngine engine;
 @Autowired JdbcTemplate jdbc;
 @Autowired RepairOrderService service;
 @Autowired RuntimeService runtime;
 @Autowired TaskService tasks;
 @Test void explicitSchemaWorksWithAutoDdlDisabled() {
  assertEquals("false",engine.getProcessEngineConfiguration().getDatabaseSchemaUpdate());
  assertEquals("7.2.0.2",jdbc.queryForObject("select value_ from act_ge_property where name_='schema.version'",String.class));
  assertEquals(30,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name like 'act_%'",Integer.class));
  assertEquals(2,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name like 'flw_%'",Integer.class));
  assertEquals(1,jdbc.queryForObject("select count(*) from dormrepair_schema_history where version='D001-flowable-7.2.0'",Integer.class));
 }
 @Test void newOrdersAreLinkedAndSyntheticHistoricalRowsRemainUntouched() {
  var jwt=Jwt.withTokenValue("test-context-only").header("alg","HS256").subject("1").claim("scope","STUDENT").build();
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  try {
   Long id=service.createRepairOrder(999L,"Migration verification","Synthetic rehearsal only","TEST");
   String pi=jdbc.queryForObject("select process_instance_id from repair_order where id=?",String.class,id);
   assertNotNull(pi);assertEquals("repair-order:"+id,runtime.createProcessInstanceQuery().processInstanceId(pi).singleResult().getBusinessKey());
   assertEquals("adminReview",tasks.createTaskQuery().processInstanceId(pi).singleResult().getTaskDefinitionKey());
   assertEquals(0,jdbc.queryForObject("select count(*) from repair_order_legacy where order_id=?",Integer.class,id));
   assertEquals(3,jdbc.queryForObject("select count(*) from repair_order where id in (1,2,3) and process_instance_id is null",Integer.class));
   assertEquals("REJECTED",jdbc.queryForObject("select status from repair_order where id=1",String.class));
   assertEquals(2,jdbc.queryForObject("select count(*) from repair_order where id in (2,3) and status='COMPLETED'",Integer.class));
   assertEquals(1,jdbc.queryForObject("select count(*) from repair_record where order_id=3 and description='Synthetic record retained'",Integer.class));
  } finally {SecurityContextHolder.clearContext();}
 }
}
