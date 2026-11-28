package com.dormrepair.v2.identity;
import java.util.*;
import java.net.*;
import java.net.http.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("v2-test") @DirtiesContext
@EnabledIfSystemProperty(named="flowable.v2B",matches="true")
class PreparationMigrationHttpTest {
 static final String DB="dorm_repair_v2_test_migrationhttpd";static final String SECRET=Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));static final java.util.concurrent.atomic.AtomicBoolean RESET=new java.util.concurrent.atomic.AtomicBoolean();
 @DynamicPropertySource static void env(DynamicPropertyRegistry r){if(RESET.compareAndSet(false,true)){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5432/"+DB,System.getenv().getOrDefault("V2_DB_USER","sota"),System.getenv().getOrDefault("V2_DB_PASSWORD",""));try(var c=ds.getConnection()){com.dormrepair.v2.platform.migration.V2DatabaseTarget.requireTestReset(c);}catch(Exception e){throw new IllegalStateException(e);}var j=new JdbcTemplate(ds);j.execute("drop schema if exists v2_business_history cascade");j.execute("drop schema public cascade");j.execute("create schema public");}r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/"+DB);r.add("V2_DB_USER",()->System.getenv().getOrDefault("V2_DB_USER","sota"));r.add("V2_DB_PASSWORD",()->System.getenv().getOrDefault("V2_DB_PASSWORD",""));r.add("V2_JWT_SECRET",()->SECRET);r.add("spring.flyway.target",()->"6");}
 @Autowired javax.sql.DataSource ds;@Autowired JdbcTemplate j;@Autowired PasswordEncoder passwords;@Autowired ObjectMapper json;@LocalServerPort int port;
 JsonNode request(String method,String path,String token,Object body,int status)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));var response=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());assertEquals(status,response.statusCode(),path);return json.readTree(response.body());}
 String login(String name,String password)throws Exception{return request("POST","/api/v2/auth/login",null,Map.of("username",name,"password",password),200).get("accessToken").asText();}
 @Test void realHttpTokensAndSharedRoleRemainIsolatedAfterV7()throws Exception{String password=UUID.randomUUID().toString();j.update("insert into public.sys_user(username,password_hash,real_name,role) values ('synthetic_admin',?,'Synthetic administrator','ADMIN'),('synthetic_student',?,'Synthetic student','STUDENT')",passwords.encode(password),passwords.encode(password));j.update("insert into public.sys_user_role select u.id,r.id from public.sys_user u cross join public.sys_role r where u.username='synthetic_admin' and r.code='STUDENT'");String admin=login("synthetic_admin",password),student=login("synthetic_student",password);request("GET","/api/v2/addresses",student,null,403);var before=j.queryForList("select rp.* from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id where r.code='STUDENT' order by rp.permission_id");String mapping="create temporary table c7_confirmed_user_grants(user_id bigint,permission_code varchar(80),primary key(user_id,permission_code));insert into c7_confirmed_user_grants select id,p from public.sys_user cross join (values ('address:own'),('residency:own'),('residency:manage'),('draft:own')) x(p) where username='synthetic_admin';insert into c7_confirmed_user_grants select id,p from public.sys_user cross join (values ('address:own'),('residency:own'),('draft:own')) x(p) where username='synthetic_student';";org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/v2/business").defaultSchema("v2_business_history").schemas("v2_business_history").group(true).initSql(mapping).load().migrate();request("GET","/api/v2/auth/me",student,null,401);request("GET","/api/v2/auth/me",admin,null,401);student=login("synthetic_student",password);admin=login("synthetic_admin",password);request("GET","/api/v2/addresses",student,null,200);request("GET","/api/v2/residencies",student,null,403);request("GET","/api/v2/residencies",admin,null,200);assertEquals(before,j.queryForList("select rp.* from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id where r.code='STUDENT' order by rp.permission_id"));assertEquals(1,new RbacRepository(j).managers());}
}
