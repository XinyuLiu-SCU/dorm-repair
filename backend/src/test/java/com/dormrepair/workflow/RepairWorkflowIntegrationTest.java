package com.dormrepair.workflow;

import com.dormrepair.service.RepairWorkflowService;
import com.dormrepair.repository.RepairOrderRepository;
import com.dormrepair.security.CurrentActor;
import com.fasterxml.jackson.databind.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.flowable.engine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123",
 "spring.datasource.username=${FLOWABLE_TEST_DB_USER:sota}", "spring.datasource.password=${FLOWABLE_TEST_DB_PASSWORD:}",
 "app.jwt.secret=stage-b-test-only-secret-at-least-32-bytes", "app.workflow.enabled=true",
 "flowable.process.enabled=true", "flowable.database-schema-update=true", "flowable.history-level=audit"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="flowable.stageB",matches="true")
class RepairWorkflowIntegrationTest {
 @DynamicPropertySource static void prepareDatabase(DynamicPropertyRegistry ignored) throws Exception {
  try(var c=DriverManager.getConnection("jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123",System.getenv().getOrDefault("FLOWABLE_TEST_DB_USER","sota"),System.getenv().getOrDefault("FLOWABLE_TEST_DB_PASSWORD",""))) {
   assertEquals("dorm_repair_flowable_a_test_20261123",c.getCatalog());
   ScriptUtils.executeSqlScript(c,new ClassPathResource("workflow/business-fixture.sql"));
   boolean migrated;
   try(var s=c.createStatement();var r=s.executeQuery("select exists(select 1 from information_schema.columns where table_schema='public' and table_name='repair_order' and column_name='process_instance_id')")){r.next();migrated=r.getBoolean(1);}
   if(!migrated) {
    // Synthetic pre-migration row proves the legacy registry is populated by migration.
    try(var s=c.createStatement()) {
     s.executeUpdate("insert into sys_user(username,password_hash,real_name,role) values ('stage_b_legacy','placeholder','Legacy fixture','STUDENT') on conflict (username) do nothing");
     s.executeUpdate("insert into repair_order(student_id,title,description,location,status) select id,'legacy fixture','before migration','test','COMPLETED' from sys_user where username='stage_b_legacy'");
    }
    ScriptUtils.executeSqlScript(c,new ClassPathResource("db/manual/B001-repair-workflow.sql"));
   }
  }
 }
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired RuntimeService runtime;
 @Autowired TaskService tasks;
 @MockitoSpyBean RepairWorkflowService workflow;
 RepairWorkflowService workflowTarget;
 long student,otherStudent,repairer,otherRepairer;
 String st,st2,rp,rp2,admin;
 @BeforeEach void users() throws Exception {
  workflowTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(workflow);
  reset(workflowTarget);
  var a=account("STUDENT");student=(Long)a[0];st=(String)a[1];
  a=account("STUDENT");otherStudent=(Long)a[0];st2=(String)a[1];
  a=account("REPAIRER");repairer=(Long)a[0];rp=(String)a[1];
  a=account("REPAIRER");otherRepairer=(Long)a[0];rp2=(String)a[1];
  admin=(String)account("ADMIN")[1];
 }
 Object[] account(String role) throws Exception {
  String name="b_"+UUID.randomUUID().toString().replace("-","");String password=UUID.randomUUID().toString();
  var hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password);
  Long id=jdbc.queryForObject("insert into sys_user(username,password_hash,real_name,role) values (?,?,?,?) returning id",Long.class,name,hash,"Stage B fixture",role);
  var login=call("/api/auth/login",null,Map.of("username",name,"password",password),200);
  return new Object[]{id,login.get("accessToken").asText()};
 }
 JsonNode call(String path,String token,Object body,int expected) throws Exception {
  var b=post(path); if(token!=null)b.header("Authorization","Bearer "+token);
  if(body!=null)b.contentType("application/json").content(json.writeValueAsString(body));
  var r=mvc.perform(b).andReturn().getResponse();
  assertEquals(expected,r.getStatus(),path+": "+r.getContentAsString());
  if(expected>=400)assertEquals(expected,json.readTree(r.getContentAsString()).get("code").asInt());
  return json.readTree(r.getContentAsString());
 }
 String path(long id,String action){return "/api/repair-orders/"+id+"/"+action;}
 long create() throws Exception {
  long id=call("/api/repair-orders",st,Map.of("studentId",otherStudent,"title","Stage B test","description","Isolated integration test","location","TEST ONLY"),200).get("id").asLong();
  assertEquals(student,jdbc.queryForObject("select student_id from repair_order where id=?",Long.class,id));
  check(id,"PENDING","adminReview");return id;
 }
 String instance(long id){return jdbc.queryForObject("select process_instance_id from repair_order where id=?",String.class,id);}
 void check(long id,String state,String node){
  assertEquals(state,jdbc.queryForObject("select status from repair_order where id=?",String.class,id));
  String pi=instance(id);assertNotNull(pi);
  var task=tasks.createTaskQuery().processInstanceId(pi).singleResult();
  if(node==null){assertNull(task);assertEquals(0,runtime.createProcessInstanceQuery().processInstanceId(pi).count());}
  else {assertNotNull(task);assertEquals(node,task.getTaskDefinitionKey());assertEquals("repair-order:"+id,runtime.createProcessInstanceQuery().processInstanceId(pi).singleResult().getBusinessKey());}
 }
 void approveAssign(long id) throws Exception {
  call(path(id,"approve"),admin,null,200);check(id,"APPROVED","adminAssign");
  call(path(id,"assign"),admin,Map.of("repairerId",repairer),200);check(id,"ASSIGNED","repairStart");
 }
 void start(long id)throws Exception {call(path(id,"start"),rp,Map.of("repairerId",otherRepairer),200);check(id,"IN_PROGRESS","repairFinish");}
 void finish(long id)throws Exception {call(path(id,"finish"),rp,Map.of("repairerId",otherRepairer,"description","Repair result"),200);check(id,"WAITING_ACCEPTANCE","studentAcceptance");}
 @Test void normalAndRepeatedOperations() throws Exception {
  long id=create();approveAssign(id);
  call(path(id,"approve"),admin,null,409);call(path(id,"assign"),admin,Map.of("repairerId",repairer),409);
  start(id);call(path(id,"start"),rp,null,409);finish(id);
  call(path(id,"finish"),rp,Map.of("description","duplicate"),409);
  call(path(id,"accept"),st,Map.of("studentId",otherStudent,"approved",true),200);check(id,"COMPLETED",null);
  assertEquals(1,jdbc.queryForObject("select count(*) from repair_record where order_id=?",Integer.class,id));
 }
 @Test void rejected() throws Exception {long id=create();call(path(id,"reject"),admin,null,200);check(id,"REJECTED",null);call(path(id,"approve"),admin,null,409);}
 @Test void twiceReworkedThenCompleted() throws Exception {
  long id=create();approveAssign(id);start(id);
  for(int i=0;i<2;i++){finish(id);call(path(id,"accept"),st,Map.of("approved",false),200);check(id,"REWORK","reworkStart");assertEquals(repairer,jdbc.queryForObject("select repairer_id from repair_order where id=?",Long.class,id));start(id);}
  finish(id);call(path(id,"accept"),st,Map.of("approved",true),200);check(id,"COMPLETED",null);
  assertEquals(3,jdbc.queryForObject("select count(*) from repair_record where order_id=?",Integer.class,id));
 }
 @Test void roleAndOwnership() throws Exception {
  long id=create();call(path(id,"approve"),st,null,403);approveAssign(id);
  call(path(id,"start"),rp2,Map.of("repairerId",repairer),403);check(id,"ASSIGNED","repairStart");
  start(id);call(path(id,"finish"),rp2,Map.of("repairerId",repairer,"description","Spoof"),403);finish(id);
  call(path(id,"accept"),st2,Map.of("studentId",student,"approved",true),403);
  call(path(id,"accept"),admin,Map.of("approved",true),403);check(id,"WAITING_ACCEPTANCE","studentAcceptance");
 }
 @Test void concurrentApprovalOnlyOnce() throws Exception {
  long id=create();var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  Callable<Integer> job=()->{gate.await();return mvc.perform(post(path(id,"approve")).header("Authorization","Bearer "+admin)).andReturn().getResponse().getStatus();};
  try{var a=executor.submit(job);var b=executor.submit(job);gate.countDown();var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(results);assertEquals(List.of(200,409),results);}
  finally{executor.shutdownNow();}check(id,"APPROVED","adminAssign");
 }
 @Test void failureAfterTaskCompletionRollsBackEverything() throws Exception {
  long id=create();approveAssign(id);start(id);var updated=jdbc.queryForObject("select updated_at from repair_order where id=?",java.sql.Timestamp.class,id);String task=tasks.createTaskQuery().processInstanceId(instance(id)).singleResult().getId();
  doAnswer(inv->{inv.callRealMethod();throw new org.flowable.common.engine.api.FlowableException("Injected after engine transition");})
   .when(workflowTarget).complete(any(RepairOrderRepository.LockedOrder.class),any(CurrentActor.class),anyMap(),eq("WAITING_ACCEPTANCE"));
  call(path(id,"finish"),rp,Map.of("description","Must rollback"),409);
  check(id,"IN_PROGRESS","repairFinish");assertEquals(task,tasks.createTaskQuery().processInstanceId(instance(id)).singleResult().getId());
  assertEquals(updated,jdbc.queryForObject("select updated_at from repair_order where id=?",java.sql.Timestamp.class,id));
  assertEquals(0,jdbc.queryForObject("select count(*) from repair_record where order_id=?",Integer.class,id));
 }
 @Test void startFailureLeavesNoUnlinkedOrder() throws Exception {
  int before=jdbc.queryForObject("select count(*) from repair_order",Integer.class);
  doAnswer(inv->{inv.callRealMethod();throw new org.flowable.common.engine.api.FlowableException("Injected start failure");}).when(workflowTarget).start(anyLong(),anyLong());
  long processes=runtime.createProcessInstanceQuery().count();
  call("/api/repair-orders",st,Map.of("title","rollback","description","rollback","location","test"),409);
  assertEquals(before,jdbc.queryForObject("select count(*) from repair_order",Integer.class));assertEquals(processes,runtime.createProcessInstanceQuery().count());
 }
 @Test void missingProcessAndWrongNodeRejected() throws Exception {
  long id=create();String pi=instance(id);
  runtime.setVariable(pi,"reviewApproved",true);tasks.complete(tasks.createTaskQuery().processInstanceId(pi).singleResult().getId());
  call(path(id,"approve"),admin,null,409);assertEquals("PENDING",jdbc.queryForObject("select status from repair_order where id=?",String.class,id));
  long orphan=create();jdbc.update("update repair_order set process_instance_id=null where id=?",orphan);call(path(orphan,"approve"),admin,null,409);
 }
 @Test void concurrentFinishOnlyOneRecord() throws Exception {
  long id=create();approveAssign(id);start(id);var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  Callable<Integer> job=()->{gate.await();return mvc.perform(post(path(id,"finish")).header("Authorization","Bearer "+rp).contentType("application/json").content("{\"description\":\"Concurrent repair\"}")).andReturn().getResponse().getStatus();};
  try{var a=executor.submit(job);var b=executor.submit(job);gate.countDown();var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(results);assertEquals(List.of(200,409),results);}
  finally{executor.shutdownNow();}
  check(id,"WAITING_ACCEPTANCE","studentAcceptance");assertEquals(1,jdbc.queryForObject("select count(*) from repair_record where order_id=?",Integer.class,id));
 }

 @Test void activeLegacyUsesOnlyBusinessStates() throws Exception {
  Long id=jdbc.queryForObject("insert into repair_order(student_id,title,description,location) values (?,'Legacy active','fixture','test') returning id",Long.class,student);
  jdbc.update("insert into repair_order_legacy(order_id) values (?)",id);
  call(path(id,"approve"),admin,null,200);call(path(id,"assign"),admin,Map.of("repairerId",repairer),200);
  call(path(id,"start"),rp,null,200);call(path(id,"finish"),rp,Map.of("description","Legacy repair"),200);
  call(path(id,"accept"),st,Map.of("approved",true),200);
  assertNull(instance(id));assertEquals("COMPLETED",jdbc.queryForObject("select status from repair_order where id=?",String.class,id));
  assertEquals(0,runtime.createProcessInstanceQuery().processInstanceBusinessKey("repair-order:"+id).count());
 }

 @Test void legacyDoesNotStartProcess() throws Exception {
  Long id=jdbc.queryForObject("select order_id from repair_order_legacy order by order_id limit 1",Long.class);
  assertNull(instance(id));call(path(id,"approve"),admin,null,409);assertNull(instance(id));
 }
}
