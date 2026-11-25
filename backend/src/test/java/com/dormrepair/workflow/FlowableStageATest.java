package com.dormrepair.workflow;
import java.util.Map;
import javax.sql.DataSource;
import org.flowable.engine.*;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123",
 "spring.datasource.username=${FLOWABLE_TEST_DB_USER:sota}", "spring.datasource.password=${FLOWABLE_TEST_DB_PASSWORD:}",
 "app.jwt.secret=stage-a-test-only-secret-at-least-32-bytes",
 "flowable.process.enabled=true", "flowable.database-schema-update=true",
 "flowable.async-executor-activate=false", "flowable.history-level=audit"
})
@EnabledIfSystemProperty(named="flowable.stageA",matches="true")
class FlowableStageATest {
 @Autowired ProcessEngine engine;
 @Autowired RepositoryService repository;
 @Autowired RuntimeService runtime;
 @Autowired TaskService tasks;
 @Autowired DataSource dataSource;
 @Autowired PlatformTransactionManager transactionManager;
 @Autowired JdbcTemplate jdbc;
 @Test void deployedAndSameTransactionResources() throws Exception {
  try(var c=dataSource.getConnection()) { assertEquals("dorm_repair_flowable_a_test_20261123",c.getCatalog()); }
  assertEquals(1,repository.createProcessDefinitionQuery().processDefinitionKey("dormRepair").latestVersion().count());
  var config=(SpringProcessEngineConfiguration)engine.getProcessEngineConfiguration();
  assertSame(transactionManager,config.getTransactionManager());
  assertSame(dataSource,jdbc.getDataSource());
  var configured=config.getDataSource();
  if(configured instanceof org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy proxy) configured=proxy.getTargetDataSource();
  assertSame(dataSource,configured);
 }
 String start() { return runtime.startProcessInstanceByKey("dormRepair","stage-a-"+java.util.UUID.randomUUID(),Map.of("studentId","101")).getId(); }
 void complete(String instance,String node,Map<String,Object> vars) {
  var task=tasks.createTaskQuery().processInstanceId(instance).singleResult(); assertNotNull(task); assertEquals(node,task.getTaskDefinitionKey());
  tasks.complete(task.getId(),vars);
 }
 @Test void engineHappyPath() {
  String id=start();complete(id,"adminReview",Map.of("reviewApproved",true));
  complete(id,"adminAssign",Map.of("repairerId","202"));complete(id,"repairStart",Map.of());
  complete(id,"repairFinish",Map.of());complete(id,"studentAcceptance",Map.of("acceptanceApproved",true));
  assertNull(runtime.createProcessInstanceQuery().processInstanceId(id).singleResult());
 }
 @Test void engineRejectionPath() {
  String id=start();complete(id,"adminReview",Map.of("reviewApproved",false));
  assertNull(runtime.createProcessInstanceQuery().processInstanceId(id).singleResult());
 }
 @Test void engineRepeatedReworkPath() {
  String id=start();complete(id,"adminReview",Map.of("reviewApproved",true));complete(id,"adminAssign",Map.of("repairerId","202"));complete(id,"repairStart",Map.of());
  for(int i=0;i<2;i++) { complete(id,"repairFinish",Map.of());complete(id,"studentAcceptance",Map.of("acceptanceApproved",false));complete(id,"reworkStart",Map.of()); }
  complete(id,"repairFinish",Map.of());complete(id,"studentAcceptance",Map.of("acceptanceApproved",true));
  assertNull(runtime.createProcessInstanceQuery().processInstanceId(id).singleResult());
 }
 @Test void engineExceptionRollsBackJdbcAndProcess() {
  // A test-only marker table; business tables are not installed during stage A.
  jdbc.execute("create table if not exists stage_a_tx_probe (id varchar(64) primary key)");
  String marker=java.util.UUID.randomUUID().toString();
  assertThrows(RuntimeException.class,()->new TransactionTemplate(transactionManager).execute(s->{
   jdbc.update("insert into stage_a_tx_probe (id) values (?)",marker);
   runtime.startProcessInstanceByKey("dormRepair",marker,Map.of("studentId","101"));
   tasks.complete("nonexistent-task-"+marker);
   return null;
  }));
  assertEquals(0,jdbc.queryForObject("select count(*) from stage_a_tx_probe where id = ?",Integer.class,marker));
  assertEquals(0,runtime.createProcessInstanceQuery().processInstanceBusinessKey(marker).count());
  assertEquals(0,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(marker).count());
 }
}
