package com.dormrepair.workflow;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** All requests use the inherited real HTTP transport on isolated port 18080. */
@EnabledIfSystemProperty(named="flowable.stageD",matches="true")
class RepairQueryHttpTest extends RepairWorkflowHttpTest {
 String query(long id,String suffix){return "/api/repair-orders/"+id+suffix;}
 @Test void detailRecordsAndWorkflowArePrivate() throws Exception {
  long id=create();
  for(String suffix:List.of("","/records","/workflow")) {
   request("GET",query(id,suffix),st,null,200);request("GET",query(id,suffix),admin,null,200);
   request("GET",query(id,suffix),st2,null,404);request("GET",query(id,suffix),rp2,null,404);
   request("GET",query(Long.MAX_VALUE,suffix),st,null,404);request("GET",query(id,suffix),null,null,401);
  }
  approveAssign(id);var detail=request("GET",query(id,""),rp,null,200);assertEquals(repairer,detail.get("repairer_id").asLong());
  for(String key:List.of("password","password_hash","passwordHash","accessToken","secret"))assertFalse(detail.has(key));
 }
 @Test void recordsIncludeEveryReworkInStableOrder() throws Exception {
  long id=create();approveAssign(id);start(id);
  for(int i=0;i<3;i++){finish(id);call(path(id,"accept"),st,Map.of("approved",i==2),200);if(i<2)start(id);}
  long previous=0;
  for(int i=0;i<3;i++) {
   var result=request("GET",query(id,"/records?page="+i+"&size=1"),st,null,200);
   assertEquals(3,result.get("total").asInt());assertEquals(1,result.get("items").size());
   long current=result.get("items").get(0).get("id").asLong();assertTrue(current>previous);previous=current;
  }
  assertEquals(0,request("GET",query(id,"/records?page=3&size=1"),st,null,200).get("items").size());
 }
 void allowed(long id,String token,String expected,String node,boolean ended)throws Exception {
  var view=request("GET",query(id,"/workflow"),token,null,200);
  assertEquals("FLOWABLE",view.get("mode").asText());assertEquals(ended,view.get("ended").asBoolean());
  assertEquals(node,node==null?null:view.get("currentNode").asText());
  assertEquals(expected,view.get("allowedActions").toString());
 }
 @Test void workflowActionsAndFinishedHistory() throws Exception {
  long id=create();allowed(id,admin,"[\"approve\",\"reject\"]","adminReview",false);allowed(id,st,"[]","adminReview",false);
  approveAssign(id);allowed(id,rp,"[\"start\"]","repairStart",false);allowed(id,admin,"[]","repairStart",false);
  start(id);allowed(id,rp,"[\"finish\"]","repairFinish",false);finish(id);allowed(id,st,"[\"accept\"]","studentAcceptance",false);
  call(path(id,"accept"),st,Map.of("approved",false),200);allowed(id,rp,"[\"start\"]","reworkStart",false);
  start(id);finish(id);call(path(id,"accept"),st,Map.of("approved",true),200);allowed(id,st,"[]",null,true);
  long rejected=create();call(path(rejected,"reject"),admin,null,200);allowed(rejected,admin,"[]",null,true);
 }
 @Test void tasksRoleScopeAndPagination() throws Exception {
  long id=create();
  assertEquals(0,request("GET","/api/workflow/tasks",st,null,200).get("total").asInt());
  var adminPage=request("GET","/api/workflow/tasks?size=1",admin,null,200);assertEquals(1,adminPage.get("items").size());assertTrue(adminPage.get("total").asLong()>=1);
  approveAssign(id);var repairTasks=request("GET","/api/workflow/tasks",rp,null,200);assertEquals(1,repairTasks.get("total").asInt());assertEquals(id,repairTasks.get("items").get(0).get("order_id").asLong());
  assertEquals(0,request("GET","/api/workflow/tasks",rp2,null,200).get("total").asInt());
  start(id);finish(id);assertEquals(1,request("GET","/api/workflow/tasks",st,null,200).get("total").asInt());
  assertEquals(0,request("GET","/api/workflow/tasks",st2,null,200).get("total").asInt());
  assertEquals(0,request("GET","/api/workflow/tasks?page=1&size=1",st,null,200).get("items").size());
  var workers=request("GET","/api/users/repairers?size=1",admin,null,200);assertEquals(1,workers.get("items").size());
  var fields=new HashSet<String>();workers.get("items").get(0).fieldNames().forEachRemaining(fields::add);assertEquals(Set.of("id","username","real_name"),fields);
  request("GET","/api/users/repairers",st,null,403);request("GET","/api/users/repairers",rp,null,403);
  request("GET","/api/workflow/tasks",null,null,401);
  for(String base:List.of("/api/workflow/tasks","/api/users/repairers",query(id,"/records"),"/api/repair-orders")) {
   for(String params:List.of("?page=-1","?size=0","?size=101","?page=100001","?size=oops"))request("GET",base+params,admin,null,400);
  }
  request("GET","/api/repair-orders?status=UNKNOWN",admin,null,400);
  assertEquals(1,request("GET","/api/repair-orders?page=0&size=1&status=WAITING_ACCEPTANCE",st,null,200).size());
 }
 @Test void legacyHasNoInventedInstance() throws Exception {
  Long id=jdbc.queryForObject("insert into repair_order(student_id,title,description,location) values (?,'D legacy','fixture','test') returning id",Long.class,student);
  jdbc.update("insert into repair_order_legacy(order_id) values (?)",id);
  var view=request("GET",query(id,"/workflow"),admin,null,200);assertEquals("LEGACY",view.get("mode").asText());assertTrue(view.get("processInstanceId").isNull());assertTrue(view.get("currentTask").isNull());assertEquals("[\"approve\",\"reject\"]",view.get("allowedActions").toString());
  Long old=jdbc.queryForObject("select order_id from repair_order_legacy order by order_id limit 1",Long.class);
  var ended=request("GET",query(old,"/workflow"),admin,null,200);assertEquals("LEGACY",ended.get("mode").asText());assertTrue(ended.get("ended").asBoolean());assertEquals(0,ended.get("allowedActions").size());
 }
 @Test void corruptAndSuspendedInstancesAreNotActionable() throws Exception {
  long id=create();runtime.suspendProcessInstanceById(instance(id));
  assertEquals(0,request("GET",query(id,"/workflow"),admin,null,200).get("allowedActions").size());
  runtime.activateProcessInstanceById(instance(id));jdbc.update("update repair_order set status='COMPLETED' where id=?",id);
  request("GET",query(id,"/workflow"),admin,null,409);
  long orphan=create();jdbc.update("update repair_order set process_instance_id=null where id=?",orphan);request("GET",query(orphan,"/workflow"),st,null,409);
 }
}
