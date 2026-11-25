package com.dormrepair.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses stage B scenario assertions, replacing transport with real TCP HTTP. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("stage-c")
@EnabledIfSystemProperty(named="flowable.stageC",matches="true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RepairWorkflowHttpTest extends RepairWorkflowIntegrationTest {
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private static final AtomicInteger REQUESTS=new AtomicInteger();
 private static final String SECRET=randomSecret();
 private static String randomSecret(){byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);return Base64.getEncoder().encodeToString(key);}
 @DynamicPropertySource static void httpProperties(DynamicPropertyRegistry registry) {
  registry.add("app.jwt.secret",()->SECRET);
  registry.add("server.port",()->0);
  registry.add("server.address",()->"127.0.0.1");
  registry.add("spring.datasource.url",WorkflowTestTargets::primaryUrl);
  registry.add("app.workflow.enabled",()->true);
  registry.add("flowable.process.enabled",()->true);
  registry.add("flowable.database-schema-update",()->true);
 }
 @Autowired JwtEncoder encoder;
 @org.springframework.boot.test.web.server.LocalServerPort int actualPort;
 private static final AtomicInteger LAST_PORT=new AtomicInteger();
 @BeforeEach void capturePort(){LAST_PORT.set(actualPort);}
 @BeforeAll static void resetCount(){REQUESTS.set(0);}
 @AfterAll static void summary(TestInfo info) throws Exception {
  java.nio.file.Files.writeString(java.nio.file.Path.of("target/"+info.getTestClass().orElseThrow().getSimpleName()+"-http-summary.txt"),
   "Transport: java.net.http.HttpClient -> real embedded Tomcat\nPort: "+LAST_PORT.get()+", bind 127.0.0.1\nDatabase: "+WorkflowTestTargets.database()+"\nRandom 32-byte test key: memory only\nCompleted HTTP requests: "+REQUESTS.get()+"\n");
 }
 HttpResponse<String> send(String method,String path,String token,Object body) throws Exception {
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+actualPort+path)).timeout(Duration.ofSeconds(20));
  if(token!=null)request.header("Authorization","Bearer "+token);
  if(body!=null)request.header("Content-Type","application/json");
  request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var response=HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());REQUESTS.incrementAndGet();return response;
 }
 JsonNode request(String method,String path,String token,Object body,int expected) throws Exception {
  var response=send(method,path,token,body);
  assertEquals(expected,response.statusCode(),method+" "+path+" HTTP status");
  var result=json.readTree(response.body());if(expected>=400)assertEquals(expected,result.get("code").asInt());return result;
 }
 @Override JsonNode call(String path,String token,Object body,int expected) throws Exception {return request("POST",path,token,body,expected);}
 @Override Object[] account(String role) throws Exception {
  var account=super.account(role);
  var me=request("GET","/api/auth/me",(String)account[1],null,200);
  assertEquals((Long)account[0],me.get("id").asLong());assertEquals(role,me.get("role").asText());
  assertFalse(me.has("password"));assertFalse(me.has("password_hash"));assertFalse(me.has("passwordHash"));return account;
 }
 @Autowired org.flowable.engine.HistoryService history;
 @Override void check(long id,String state,String node) {
  super.check(id,state,node);
  String pi=instance(id);
  var historical=history.createHistoricProcessInstanceQuery().processInstanceId(pi).singleResult();
  assertNotNull(historical);assertEquals("repair-order:"+id,historical.getBusinessKey());
  if(node==null)assertNotNull(historical.getEndTime());
  else {
   var task=tasks.createTaskQuery().processInstanceId(pi).singleResult();
   String expected=switch(state){case "ASSIGNED","IN_PROGRESS","REWORK"->Long.toString(repairer);case "WAITING_ACCEPTANCE"->Long.toString(student);default->null;};
   assertEquals(expected,task.getAssignee());
  }
  for(var record:jdbc.queryForList("select repairer_id, description from repair_record where order_id=?",id)) {
   assertEquals(repairer,((Number)record.get("repairer_id")).longValue());
   assertFalse(record.get("description").toString().isBlank());
  }
 }
 @Override @Test void startFailureLeavesNoUnlinkedOrder() throws Exception {
  long before=history.createHistoricProcessInstanceQuery().count();
  super.startFailureLeavesNoUnlinkedOrder();
  assertEquals(before,history.createHistoricProcessInstanceQuery().count());
 }
 void concurrently(long id,String action,String token,Object body) throws Exception {
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  Callable<Integer> job=()->{gate.await();var response=send("POST",path(id,action),token,body);if(response.statusCode()==409)assertEquals(409,json.readTree(response.body()).get("code").asInt());return response.statusCode();};
  try {var a=pool.submit(job);var b=pool.submit(job);gate.countDown();var statuses=new ArrayList<>(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}
  finally{pool.shutdownNow();}
 }
 @Override @Test void concurrentApprovalOnlyOnce() throws Exception {long id=create();concurrently(id,"approve",admin,null);check(id,"APPROVED","adminAssign");}
 @Override @Test void concurrentFinishOnlyOneRecord() throws Exception {
  long id=create();approveAssign(id);start(id);concurrently(id,"finish",rp,Map.of("description","Real HTTP concurrent repair"));check(id,"WAITING_ACCEPTANCE","studentAcceptance");
  assertEquals(1,jdbc.queryForObject("select count(*) from repair_record where order_id=?",Integer.class,id));
 }
 @Test void anonymousMalformedExpiredAndWrongSignature() throws Exception {
  request("GET","/api/auth/me",null,null,401);request("GET","/api/auth/me","invalid",null,401);
  var now=Instant.now();var claims=JwtClaimsSet.builder().issuer("dorm-repair").subject(Long.toString(student)).issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).claim("scope","STUDENT").build();
  var header=JwsHeader.with(MacAlgorithm.HS256).build();
  String expired=encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();request("GET","/api/auth/me",expired,null,401);
  var foreign=new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(new javax.crypto.spec.SecretKeySpec(randomSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")));
  var validClaims=JwtClaimsSet.builder().issuer("dorm-repair").subject(Long.toString(student)).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("scope","STUDENT").build();
  request("GET","/api/auth/me",foreign.encode(JwtEncoderParameters.from(header,validClaims)).getTokenValue(),null,401);
  call("/api/auth/login",null,Map.of("username","missing_"+UUID.randomUUID(),"password","incorrect"),401);
 }
 @Test void realHttpListIsolation() throws Exception {
  long id=create();approveAssign(id);
  for(var entry:Map.of(st,true,st2,false,rp,true,rp2,false,admin,true).entrySet()) {
   var rows=request("GET","/api/repair-orders",entry.getKey(),null,200);boolean found=false;
   for(var row:rows)if(row.get("id").asLong()==id)found=true;
   assertEquals(entry.getValue(),found);
  }
 }
 @Test void malformedAndInvalidFields() throws Exception {
  call("/api/repair-orders",st,Map.of("title","","description","test","location","test"),400);
  call("/api/auth/login",null,Map.of(),400);
  var r=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/api/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{" )).build(),HttpResponse.BodyHandlers.ofString());
  REQUESTS.incrementAndGet();assertEquals(400,r.statusCode());assertEquals(400,json.readTree(r.body()).get("code").asInt());
 }
}
