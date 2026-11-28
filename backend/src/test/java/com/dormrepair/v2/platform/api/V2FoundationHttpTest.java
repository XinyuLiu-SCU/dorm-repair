package com.dormrepair.v2.platform.api;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("v2-test")
@EnabledIfSystemProperty(named="flowable.v2A",matches="true")
@DirtiesContext
class V2FoundationHttpTest {
 private static final HttpClient HTTP=HttpClient.newHttpClient();
 private static final String SECRET=randomSecret();
 private static String randomSecret(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
 @DynamicPropertySource static void environment(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test_foundatione");r.add("V2_DB_USER",()->System.getenv().getOrDefault("V2_DB_USER","sota"));r.add("V2_DB_PASSWORD",()->System.getenv().getOrDefault("V2_DB_PASSWORD",""));r.add("V2_JWT_SECRET",()->SECRET);}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired ApplicationContext context;
 String token;long actor;
 @BeforeEach void syntheticLogin()throws Exception{
  String username="v2a_"+UUID.randomUUID().toString().replace("-","");String password=UUID.randomUUID().toString();
  actor=jdbc.queryForObject("insert into public.sys_user(username,password_hash,real_name,role) values (?,?,?,?) returning id",Long.class,username,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),"Synthetic V2-A student","STUDENT");
  var login=send("POST","/api/auth/login",null,Map.of("username",username,"password",password),Map.of());assertEquals(200,login.statusCode());token=json.readTree(login.body()).get("accessToken").asText();
 }
 private HttpResponse<String> send(String method,String path,String token,Object body,Map<String,String> headers)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);headers.forEach(b::header);
  if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void authorizedInfoUsesIsolatedMigrationAndContainsNoPrivateConfiguration()throws Exception{
  var response=send("GET","/api/v2/system/info",token,null,Map.of("X-Request-Id","review_trace_123"));assertEquals(200,response.statusCode());var info=json.readTree(response.body());assertEquals("V2.0 Final — E",info.get("stage").asText());assertEquals("11",info.get("businessMigrationVersion").asText());assertFalse(info.get("workflowEnabled").asBoolean());assertEquals("review_trace_123",response.headers().firstValue("X-Request-Id").orElseThrow());
  for(String field:List.of("database","password","username","secret","url"))assertFalse(info.has(field));
  assertTrue(context.getBeansOfType(org.flowable.engine.ProcessEngine.class).isEmpty());assertEquals(0,jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and (table_name like 'act_%' or table_name like 'flw_%')",Integer.class));
 }
 @Test void v2AuthenticationErrorsAreTrackedAndV1ShapeRemainsNumeric()throws Exception{
  for(String candidate:new String[]{null,"invalid"}){var response=send("GET","/api/v2/system/info",candidate,null,Map.of());assertEquals(401,response.statusCode());var e=json.readTree(response.body());assertEquals(401,e.get("status").asInt());assertEquals("UNAUTHORIZED",e.get("code").asText());assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(),e.get("requestId").asText());assertTrue(e.get("fieldErrors").isArray());}
  var v1=send("GET","/api/auth/me",null,null,Map.of());assertEquals(401,v1.statusCode());assertTrue(json.readTree(v1.body()).get("code").isIntegralNumber());
  var me=send("GET","/api/auth/me",token,null,Map.of());assertEquals(200,me.statusCode());assertEquals(actor,json.readTree(me.body()).get("id").asLong());assertTrue(json.readTree(me.body()).get("id").isIntegralNumber());
 }
 @Test void forbiddenV2OperationUsesV2Contract()throws Exception{var r=send("POST","/api/v2/system/info",token,Map.of(),Map.of());assertEquals(403,r.statusCode());assertEquals("FORBIDDEN",json.readTree(r.body()).get("code").asText());}
 @Test void v2CorsAllowsTraceHeaderWithoutChangingV1Security()throws Exception{var r=send("OPTIONS","/api/v2/system/info",null,null,Map.of("Origin","http://localhost:5173","Access-Control-Request-Method","GET","Access-Control-Request-Headers","authorization,x-request-id"));assertEquals(200,r.statusCode());assertEquals("http://localhost:5173",r.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());}
}
