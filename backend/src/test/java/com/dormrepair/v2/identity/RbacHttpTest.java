package com.dormrepair.v2.identity;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.AopTestUtils;
import com.dormrepair.v2.platform.migration.V2DatabaseTarget;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("v2-test")
@EnabledIfSystemProperty(named="flowable.v2B",matches="true")
@DirtiesContext
class RbacHttpTest {
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
 private static final AtomicInteger REQUESTS=new AtomicInteger();
 private static final AtomicInteger PORT=new AtomicInteger();
 private static final String SECRET=random();
 private static String random(){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
 @DynamicPropertySource static void env(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test_rbac");r.add("V2_DB_USER",()->System.getenv().getOrDefault("V2_DB_USER","sota"));r.add("V2_DB_PASSWORD",()->System.getenv().getOrDefault("V2_DB_PASSWORD",""));r.add("V2_JWT_SECRET",()->SECRET);}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired javax.sql.DataSource dataSource;
 @Autowired PlatformTransactionManager transactionManager;
 @Autowired ObjectMapper json;
 @Autowired PasswordEncoder passwords;
 @Autowired JwtEncoder encoder;
 @MockitoSpyBean SecurityAudit audit;
 SecurityAudit auditTarget;
 String admin;String adminName;String password;long adminId;
 record Reply(int status,JsonNode body,java.net.http.HttpHeaders headers){}
 Reply raw(String method,String path,String token,Object body)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
  if(token!=null)b.header("Authorization","Bearer "+token);b.header("X-Request-Id","rbac_http_test_"+REQUESTS.incrementAndGet());
  if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),json.readTree(r.body()),r.headers());
 }
 JsonNode call(String method,String path,String token,Object body,int status)throws Exception{var r=raw(method,path,token,body);assertEquals(status,r.status(),method+" "+path);return r.body();}
 String login(String username,String password)throws Exception{return call("POST","/api/v2/auth/login",null,Map.of("username",username,"password",password),200).get("accessToken").asText();}
 long roleId(String code){return jdbc.queryForObject("select id from public.sys_role where code=?",Long.class,code);}
 long userVersion(long id){return jdbc.queryForObject("select version from public.sys_user where id=?",Long.class,id);}
 long roleVersion(long id){return jdbc.queryForObject("select version from public.sys_role where id=?",Long.class,id);}
 long authVersion(long id){return jdbc.queryForObject("select auth_version from public.sys_user where id=?",Long.class,id);}
 long managerCount(){return new RbacRepository(jdbc).managers();}
 String newUsername(){return "b_"+UUID.randomUUID().toString().replace("-","");}
 Set<String> allPermissions(){return new TreeSet<>(jdbc.queryForList("select code from public.sys_permission where available",String.class));}
 long createRole(Set<String> permissions)throws Exception{return Long.parseLong(call("POST","/api/v2/roles",admin,Map.of("code","R_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"name","Synthetic role","permissionCodes",permissions),200).get("id").asText());}
 record Account(long id,String username,String token){}
 Account createUser(List<Long> roles,String legacy)throws Exception{
  String name=newUsername();var dto=call("POST","/api/v2/users",admin,Map.of("username",name,"password",password,"realName","Synthetic user","phone","TEST-000","legacyRole",legacy,"roleIds",roles.stream().map(String::valueOf).toList()),200);
  assertTrue(dto.get("id").isTextual());return new Account(Long.parseLong(dto.get("id").asText()),name,login(name,password));
 }
 @BeforeEach void fixtures()throws Exception{
  PORT.set(port);auditTarget=AopTestUtils.getUltimateTargetObject(audit);reset(auditTarget);
  new TransactionTemplate(transactionManager).execute(status->{
   jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{V2DatabaseTarget.requireTestReset(c);if(!c.getCatalog().equals("dorm_repair_v2_test_rbac"))throw new IllegalStateException("Wrong fixture database");return null;});
   jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);
   jdbc.execute("truncate public.audit_event");jdbc.update("update public.system_guard set initialized_user_id=null,version=0");
   jdbc.update("delete from public.repair_record");jdbc.update("delete from public.repair_order_legacy");jdbc.update("delete from public.repair_order");jdbc.update("delete from public.sys_user_role");jdbc.update("delete from public.sys_user");
   jdbc.update("delete from public.sys_role_permission");jdbc.update("delete from public.sys_role where code not in ('STUDENT','REPAIRER','ADMIN')");jdbc.update("update public.sys_role set enabled=true,version=0");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code='ADMIN'");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code in ('STUDENT','REPAIRER') and (p.code in ('profile:read','profile:write','profile:password') or (r.code='STUDENT' and p.code='legacy:student') or (r.code='REPAIRER' and p.code='legacy:repairer'))");return null;
  });
  password=UUID.randomUUID().toString();adminName=newUsername();var result=BootstrapAdministrator.initializeForTest(dataSource,passwords,adminName,password);adminId=Long.parseLong(result.userId());admin=login(adminName,password);
 }
 @AfterAll static void summary()throws Exception{java.nio.file.Files.writeString(java.nio.file.Path.of("target/V2.0-B-http-summary.json"),"{\"transport\":\"real HTTP and PostgreSQL\",\"requests\":"+REQUESTS.get()+",\"port\":"+PORT.get()+",\"database\":\"dorm_repair_v2_test_rbac\"}");}
 @Test void permissionSeedsMatchOnlyImplementedInterfaces()throws Exception{var p=call("GET","/api/v2/permissions?size=100",admin,null,200);assertEquals(10,p.get("total").asInt());for(var item:p.get("items")){assertTrue(item.get("id").isTextual());assertTrue(item.get("available").asBoolean());assertFalse(item.get("code").asText().startsWith("order:"));}assertEquals("3",call("GET","/api/v2/system/info",admin,null,200).get("businessMigrationVersion").asText());}
 @Test void multipleRolePermissionsAreUnionNotRoleNames()throws Exception{
  long first=createRole(Set.of("profile:read")),second=createRole(Set.of("permission:read"));var a=createUser(List.of(first,second),"STUDENT");var me=call("GET","/api/v2/auth/me",a.token(),null,200);assertEquals(2,me.get("roles").size());assertEquals(Set.of("profile:read","permission:read"),json.convertValue(me.get("permissions"),new com.fasterxml.jackson.core.type.TypeReference<Set<String>>(){}));call("GET","/api/v2/profile",a.token(),null,200);call("GET","/api/v2/permissions",a.token(),null,200);call("GET","/api/v2/users",a.token(),null,403);
 }
 @Test void fakeAdministratorLabelAndLegacyClaimDoNotGrantPower()throws Exception{
  long r=createRole(Set.of("profile:read"));call("PATCH","/api/v2/roles/"+r,admin,Map.of("version",roleVersion(r),"name","ADMIN","enabled",true),200);var a=createUser(List.of(r),"ADMIN");call("GET","/api/v2/users",a.token(),null,403);call("GET","/api/users/repairers",a.token(),null,403);
  var now=java.time.Instant.now();var claims=JwtClaimsSet.builder().issuer("dorm-repair").subject(Long.toString(a.id())).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("authVersion",authVersion(a.id())).claim("scope","ADMIN").claim("username",a.username()).build();
  String forgedScope=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();call("GET","/api/users/repairers",forgedScope,null,403);
 }
 @Test void roleDisableRevokesOldTokenAndNewLoginLosesPermission()throws Exception{
  long r=createRole(Set.of("profile:read","permission:read"));var a=createUser(List.of(r),"STUDENT");call("POST","/api/v2/roles/"+r+"/disable",admin,Map.of("version",roleVersion(r)),200);call("GET","/api/v2/auth/me",a.token(),null,401);String fresh=login(a.username(),password);call("GET","/api/v2/permissions",fresh,null,403);assertEquals(0,call("GET","/api/v2/auth/me",fresh,null,200).get("permissions").size());
 }
 @Test void permissionRemovalAndReenableNeverReviveOldTokens()throws Exception{
  long r=createRole(Set.of("profile:read","permission:read"));var a=createUser(List.of(r),"STUDENT");call("PUT","/api/v2/roles/"+r+"/permissions",admin,Map.of("version",roleVersion(r),"permissionCodes",List.of("profile:read")),200);call("GET","/api/v2/auth/me",a.token(),null,401);String fresh=login(a.username(),password);call("GET","/api/v2/permissions",fresh,null,403);call("POST","/api/v2/roles/"+r+"/disable",admin,Map.of("version",roleVersion(r)),200);call("POST","/api/v2/roles/"+r+"/enable",admin,Map.of("version",roleVersion(r)),200);call("GET","/api/v2/auth/me",fresh,null,401);call("GET","/api/v2/auth/me",a.token(),null,401);
 }

 @Test void removingOnlyLegacyAdminCapabilityCannotBeBypassedThroughV1()throws Exception{
  long role=createRole(allPermissions());var a=createUser(List.of(role),"ADMIN");var perms=allPermissions();perms.remove("legacy:admin");call("PUT","/api/v2/roles/"+role+"/permissions",admin,Map.of("version",roleVersion(role),"permissionCodes",perms),200);call("GET","/api/users/repairers",a.token(),null,401);String fresh=login(a.username(),password);call("GET","/api/v2/users",fresh,null,200);call("GET","/api/users/repairers",fresh,null,403);call("GET","/api/auth/me",fresh,null,403);call("GET","/api/v2/auth/me",fresh,null,200);
 }
 @Test void userDisableInvalidatesBothV1AndV2AndBlocksLogin()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");call("POST","/api/v2/users/"+a.id()+"/disable",admin,Map.of("version",userVersion(a.id())),200);call("GET","/api/v2/auth/me",a.token(),null,401);call("GET","/api/auth/me",a.token(),null,401);call("POST","/api/auth/login",null,Map.of("username",a.username(),"password",password),401);call("POST","/api/v2/auth/login",null,Map.of("username",a.username(),"password",password),401);call("POST","/api/v2/users/"+a.id()+"/enable",admin,Map.of("version",userVersion(a.id())),200);call("GET","/api/v2/auth/me",a.token(),null,401);call("GET","/api/auth/me",login(a.username(),password),null,200);
 }
 @Test void userRoleAssignmentRevokesSessionAndV1Capability()throws Exception{
  var a=createUser(List.of(roleId("REPAIRER")),"REPAIRER");call("GET","/api/repair-orders",a.token(),null,200);call("PUT","/api/v2/users/"+a.id()+"/roles",admin,Map.of("version",userVersion(a.id()),"roleIds",List.of()),200);call("GET","/api/repair-orders",a.token(),null,401);String fresh=login(a.username(),password);call("GET","/api/repair-orders",fresh,null,403);assertEquals(0,call("GET","/api/v2/auth/me",fresh,null,200).get("permissions").size());
 }
 @Test void unversionedTokensRequireReloginInBothNamespaces()throws Exception{
  var now=java.time.Instant.now();var claims=JwtClaimsSet.builder().issuer("dorm-repair").subject(Long.toString(adminId)).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("scope","ADMIN").claim("username",adminName).build();String old=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();call("GET","/api/auth/me",old,null,401);call("GET","/api/v2/auth/me",old,null,401);
 }
 @Test void versionedV1LoginAndFixedResponseRemainCompatible()throws Exception{
  String token=call("POST","/api/auth/login",null,Map.of("username",adminName,"password",password),200).get("accessToken").asText();var me=call("GET","/api/auth/me",token,null,200);assertTrue(me.get("id").isIntegralNumber());assertEquals("ADMIN",me.get("role").asText());call("GET","/api/users/repairers",token,null,200);
 }
 @Test void targetHierarchyPreventsCrossUserAndRoleTakeover()throws Exception{
  long limited=createRole(Set.of("user:manage","role:manage","profile:read"));var a=createUser(List.of(limited),"STUDENT");call("GET","/api/v2/users/"+adminId,a.token(),null,404);call("POST","/api/v2/users/"+adminId+"/disable",a.token(),Map.of("version",userVersion(adminId)),404);call("POST","/api/v2/users/"+adminId+"/reset-password",a.token(),Map.of("version",userVersion(adminId),"newPassword",UUID.randomUUID().toString()),404);call("PUT","/api/v2/roles/"+roleId("ADMIN")+"/permissions",a.token(),Map.of("version",roleVersion(roleId("ADMIN")),"permissionCodes",List.of()),404);
  var page=call("GET","/api/v2/users?size=100",a.token(),null,200);for(var item:page.get("items"))assertNotEquals(Long.toString(adminId),item.get("id").asText());
 }
 @Test void grantAndSelfAssignmentEscalationAreDenied()throws Exception{
  long limited=createRole(Set.of("user:manage","role:manage","profile:read"));var a=createUser(List.of(limited),"STUDENT");call("POST","/api/v2/roles",a.token(),Map.of("code","ESCALATION","name","Not allowed","permissionCodes",List.of("audit:read")),403);call("PUT","/api/v2/roles/"+limited+"/permissions",a.token(),Map.of("version",roleVersion(limited),"permissionCodes",List.of("profile:read","role:manage","user:manage","audit:read")),403);call("PUT","/api/v2/users/"+a.id()+"/roles",a.token(),Map.of("version",userVersion(a.id()),"roleIds",List.of(Long.toString(roleId("ADMIN")))),403);
 }

 @Test void lowPrivilegeRoleCannotBeUsedToMutateAStrongerAssignedUser()throws Exception{
  long low=createRole(Set.of("profile:read"));long operator=createRole(Set.of("profile:read","user:manage","role:manage"));var a=createUser(List.of(operator),"STUDENT");jdbc.update("insert into public.sys_user_role(user_id,role_id) values (?,?)",adminId,low);long before=authVersion(adminId);call("PATCH","/api/v2/roles/"+low,a.token(),Map.of("version",roleVersion(low),"name","Unauthorized rename","enabled",true),403);assertEquals(before,authVersion(adminId));assertEquals(0,roleVersion(low));
 }
 @Test void disabledPowerfulRoleStillProtectsTargetFromLowerOperator()throws Exception{
  long powerful=createRole(allPermissions());call("POST","/api/v2/roles/"+powerful+"/disable",admin,Map.of("version",roleVersion(powerful)),200);var dormant=createUser(List.of(powerful),"ADMIN");long limited=createRole(Set.of("user:manage","role:manage"));var op=createUser(List.of(limited),"STUDENT");call("POST","/api/v2/users/"+dormant.id()+"/reset-password",op.token(),Map.of("version",userVersion(dormant.id()),"newPassword",UUID.randomUUID().toString()),404);
 }
 @Test void lastManagerRoleDisableAndKeyPermissionRemovalRollBack()throws Exception{
  long role=roleId("ADMIN");long before=authVersion(adminId);call("POST","/api/v2/roles/"+role+"/disable",admin,Map.of("version",roleVersion(role)),409);var p=allPermissions();p.remove("role:manage");call("PUT","/api/v2/roles/"+role+"/permissions",admin,Map.of("version",roleVersion(role),"permissionCodes",p),409);assertEquals(before,authVersion(adminId));assertEquals(1,managerCount());call("GET","/api/v2/auth/me",admin,null,200);
 }
 List<Integer> concurrent(Callable<Integer> first,Callable<Integer> second)throws Exception{
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{start.await();return first.call();});var b=pool.submit(()->{start.await();return second.call();});start.countDown();var result=new ArrayList<>(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS)));Collections.sort(result);return result;}finally{pool.shutdownNow();}
 }
 @Test void concurrentAdminsCannotDisableEachOtherToZero()throws Exception{
  var b=createUser(List.of(roleId("ADMIN")),"ADMIN");long av=userVersion(adminId),bv=userVersion(b.id());var statuses=concurrent(()->raw("POST","/api/v2/users/"+b.id()+"/disable",admin,Map.of("version",bv)).status(),()->raw("POST","/api/v2/users/"+adminId+"/disable",b.token(),Map.of("version",av)).status());assertEquals(List.of(200,401),statuses);assertEquals(1,managerCount());
 }
 @Test void concurrentAdminsCannotRemoveEachOthersRolesToZero()throws Exception{
  var b=createUser(List.of(roleId("ADMIN")),"ADMIN");long av=userVersion(adminId),bv=userVersion(b.id());var statuses=concurrent(()->raw("PUT","/api/v2/users/"+b.id()+"/roles",admin,Map.of("version",bv,"roleIds",List.of())).status(),()->raw("PUT","/api/v2/users/"+adminId+"/roles",b.token(),Map.of("version",av,"roleIds",List.of())).status());assertEquals(List.of(200,401),statuses);assertEquals(1,managerCount());
 }
 @Test void concurrentCriticalPermissionChangesKeepOneManager()throws Exception{
  long ar=createRole(allPermissions()),br=createRole(allPermissions());jdbc.update("delete from public.sys_user_role where user_id=?",adminId);jdbc.update("insert into public.sys_user_role(user_id,role_id) values (?,?)",adminId,ar);admin=login(adminName,password);var b=createUser(List.of(br),"ADMIN");var codes=allPermissions();codes.remove("role:manage");String aToken=admin;long av=roleVersion(ar),bv=roleVersion(br);
  var statuses=concurrent(()->raw("PUT","/api/v2/roles/"+br+"/permissions",aToken,Map.of("version",bv,"permissionCodes",codes)).status(),()->raw("PUT","/api/v2/roles/"+ar+"/permissions",b.token(),Map.of("version",av,"permissionCodes",codes)).status());assertEquals(List.of(200,401),statuses);assertEquals(1,managerCount());
 }
 @Test void concurrentDisableOfSharedLastManagerRoleBothFailsSafely()throws Exception{
  var b=createUser(List.of(roleId("ADMIN")),"ADMIN");long role=roleId("ADMIN"),version=roleVersion(role);assertEquals(List.of(409,409),concurrent(()->raw("POST","/api/v2/roles/"+role+"/disable",admin,Map.of("version",version)).status(),()->raw("POST","/api/v2/roles/"+role+"/disable",b.token(),Map.of("version",version)).status()));assertEquals(2,managerCount());
 }
 @Test void passwordChangeRevokesAllOldSessionsAndNeverLeaksSecret()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");String newPassword=UUID.randomUUID().toString();var response=call("POST","/api/v2/profile/password",a.token(),Map.of("version",userVersion(a.id()),"oldPassword",password,"newPassword",newPassword),200);assertTrue(response.get("reloginRequired").asBoolean());assertFalse(response.toString().contains(newPassword));call("GET","/api/auth/me",a.token(),null,401);call("POST","/api/v2/auth/login",null,Map.of("username",a.username(),"password",password),401);call("GET","/api/v2/profile",login(a.username(),newPassword),null,200);
  String auditBody=call("GET","/api/v2/audit-events?size=100",admin,null,200).toString();assertFalse(auditBody.contains(password));assertFalse(auditBody.contains(newPassword));assertFalse(auditBody.contains("password_hash"));assertFalse(auditBody.contains(a.token()));
 }

 @Test void migratedNonAdminCanSecureOwnPasswordWithoutAnExistingCustodian()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");jdbc.update("update public.sys_user set enabled=false,auth_version=auth_version+1 where id=?",adminId);assertEquals(0,managerCount());String fresh=UUID.randomUUID().toString();call("POST","/api/v2/profile/password",a.token(),Map.of("version",userVersion(a.id()),"oldPassword",password,"newPassword",fresh),200);call("GET","/api/v2/auth/me",a.token(),null,401);call("GET","/api/v2/profile",login(a.username(),fresh),null,200);
 }
 @Test void adminResetRevokesTokenAndSelfResetIsDenied()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");String fresh=UUID.randomUUID().toString();call("POST","/api/v2/users/"+a.id()+"/reset-password",admin,Map.of("version",userVersion(a.id()),"newPassword",fresh),200);call("GET","/api/v2/auth/me",a.token(),null,401);call("POST","/api/v2/auth/login",null,Map.of("username",a.username(),"password",password),401);call("GET","/api/v2/auth/me",login(a.username(),fresh),null,200);call("POST","/api/v2/users/"+adminId+"/reset-password",admin,Map.of("version",userVersion(adminId),"newPassword",fresh),403);
 }
 @Test void invalidPasswordAndUnknownActorOrNumericRoleIdsAreRejected()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");for(String bad:List.of("x".repeat(11),"中".repeat(25)))call("POST","/api/v2/profile/password",a.token(),Map.of("version",userVersion(a.id()),"oldPassword",password,"newPassword",bad),400);
  call("PATCH","/api/v2/profile",a.token(),Map.of("version",userVersion(a.id()),"realName","Synthetic","actorId",Long.toString(adminId)),400);
  call("PUT","/api/v2/users/"+a.id()+"/roles",admin,Map.of("version",userVersion(a.id()),"roleIds",List.of(roleId("STUDENT"))),400);
 }
 @Test void profileOwnDataAndAdminUserEditsRespectVersions()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");long old=userVersion(a.id());var dto=call("PATCH","/api/v2/profile",a.token(),Map.of("version",old,"realName","New synthetic name","phone","TEST-001"),200);assertEquals("New synthetic name",dto.get("realName").asText());assertEquals(0,authVersion(a.id()));call("PATCH","/api/v2/profile",a.token(),Map.of("version",old,"realName","stale"),409);call("PATCH","/api/v2/users/"+a.id(),admin,Map.of("version",userVersion(a.id()),"realName","Admin edited"),200);call("GET","/api/v2/users/"+adminId,a.token(),null,403);
 }

 @Test void omittedPhoneIsPreservedAndExplicitNullClearsOnlyOwnPhone()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");call("PATCH","/api/v2/profile",a.token(),Map.of("version",userVersion(a.id()),"realName","Preserved"),200);assertEquals("TEST-000",call("GET","/api/v2/profile",a.token(),null,200).get("phone").asText());var clear=new LinkedHashMap<String,Object>();clear.put("version",userVersion(a.id()));clear.put("realName","Cleared");clear.put("phone",null);call("PATCH","/api/v2/profile",a.token(),clear,200);assertTrue(call("GET","/api/v2/profile",a.token(),null,200).get("phone").isNull());
 }
 @Test void usersRolesPermissionsAndAuditPaginationAreBounded()throws Exception{
  createUser(List.of(roleId("STUDENT")),"STUDENT");for(String path:List.of("users","roles","permissions","audit-events")){var page=call("GET","/api/v2/"+path+"?size=1",admin,null,200);assertEquals(1,page.get("size").asInt());assertTrue(page.get("items").size()<=1);call("GET","/api/v2/"+path+"?size=101",admin,null,400);call("GET","/api/v2/"+path+"?page=-1",admin,null,400);assertEquals(0,call("GET","/api/v2/"+path+"?page=99999",admin,null,200).get("items").size());}
  var page=call("GET","/api/v2/users",admin,null,200);assertFalse(page.toString().contains("passwordHash"));for(var u:page.get("items")){assertFalse(u.has("phone"));assertTrue(u.get("id").isTextual());}
 }
 @Test void roleRenameRevokesAssignedUsersAndRoleCodeIsImmutable()throws Exception{
  long r=createRole(Set.of("profile:read"));var a=createUser(List.of(r),"STUDENT");call("PATCH","/api/v2/roles/"+r,admin,Map.of("version",roleVersion(r),"name","Renamed synthetic","enabled",true),200);call("GET","/api/v2/auth/me",a.token(),null,401);call("PATCH","/api/v2/roles/"+r,admin,Map.of("version",roleVersion(r),"name","X","enabled",true,"code","ADMIN"),400);
 }
 @Test void duplicateUserRoleAndUnknownPermissionDoNotCreatePartialData()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");long count=jdbc.queryForObject("select count(*) from public.sys_user",Long.class);
  call("POST","/api/v2/users",admin,Map.of("username",a.username(),"password",password,"realName","Duplicate","roleIds",List.of()),409);assertEquals(count,jdbc.queryForObject("select count(*) from public.sys_user",Long.class));call("POST","/api/v2/roles",admin,Map.of("code","UNKNOWN_GRANT","name","Unknown","permissionCodes",List.of("order:not-implemented")),400);
 }
 @Test void auditFailureRollsBackPermissionsVersionsAndSessionRevocation()throws Exception{
  long r=createRole(Set.of("profile:read","permission:read"));var a=createUser(List.of(r),"STUDENT");long before=authVersion(a.id()),rv=roleVersion(r),av=userVersion(a.id());long audits=jdbc.queryForObject("select count(*) from public.audit_event",Long.class);
  doThrow(new IllegalStateException("injected audit failure")).when(auditTarget).record(any(),eq("ROLE_PERMISSIONS_CHANGED"),anyString(),any(),anyMap());
  call("PUT","/api/v2/roles/"+r+"/permissions",admin,Map.of("version",rv,"permissionCodes",List.of("profile:read")),500);
  assertEquals(before,authVersion(a.id()));assertEquals(rv,roleVersion(r));assertEquals(av,userVersion(a.id()));assertEquals(audits,jdbc.queryForObject("select count(*) from public.audit_event",Long.class));call("GET","/api/v2/permissions",a.token(),null,200);
 }
 @Test void auditIsAppendOnlyAndDoesNotExposePrivateContactData()throws Exception{
  var a=createUser(List.of(roleId("STUDENT")),"STUDENT");var view=call("GET","/api/v2/audit-events?size=100",admin,null,200);assertFalse(view.toString().contains("TEST-000"));assertFalse(view.toString().contains(password));call("GET","/api/v2/audit-events",a.token(),null,403);assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update public.audit_event set action='changed'"));
 }
 @Test void repeatedInitializerNeverOverwritesPasswordAndCannotChangeIdentity()throws Exception{
  String before=jdbc.queryForObject("select password_hash from public.sys_user where id=?",String.class,adminId);var again=BootstrapAdministrator.initializeForTest(dataSource,passwords,adminName,UUID.randomUUID().toString());assertEquals("ALREADY_INITIALIZED",again.state());assertEquals(before,jdbc.queryForObject("select password_hash from public.sys_user where id=?",String.class,adminId));assertEquals(1,jdbc.queryForObject("select count(*) from public.sys_user",Integer.class));assertThrows(IllegalStateException.class,()->BootstrapAdministrator.initializeForTest(dataSource,passwords,newUsername(),password));assertThrows(IllegalStateException.class,()->BootstrapAdministrator.initialize(dataSource,passwords,adminName,password));
 }

 @Test void recoveryCustodianCannotLoseEvenNonCriticalGrantToZero()throws Exception{
  long role=roleId("ADMIN"),av=authVersion(adminId);var codes=allPermissions();codes.remove("audit:read");call("PUT","/api/v2/roles/"+role+"/permissions",admin,Map.of("version",roleVersion(role),"permissionCodes",codes),409);assertEquals(av,authVersion(adminId));assertEquals(1,managerCount());call("GET","/api/v2/audit-events",admin,null,200);
 }
 @Test void recoveryCustodianMayBeTheUnionOfSeveralRoles()throws Exception{
  var all=new ArrayList<>(allPermissions());Set<String> left=new TreeSet<>(all.subList(0,5)),right=new TreeSet<>(all.subList(5,all.size()));long a=createRole(left),b=createRole(right);jdbc.update("delete from public.sys_user_role where user_id=?",adminId);jdbc.update("insert into public.sys_user_role(user_id,role_id) values (?,?),(?,?)",adminId,a,adminId,b);admin=login(adminName,password);assertEquals(1,managerCount());assertEquals(allPermissions(),json.convertValue(call("GET","/api/v2/auth/me",admin,null,200).get("permissions"),new com.fasterxml.jackson.core.type.TypeReference<Set<String>>(){}));
 }
 private void emptyBootstrapFixture(){new TransactionTemplate(transactionManager).execute(status->{jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);jdbc.execute("truncate public.audit_event");jdbc.update("update public.system_guard set initialized_user_id=null");jdbc.update("delete from public.sys_user_role");jdbc.update("delete from public.sys_user");return null;});}
 @Test void invalidFirstBootstrapIsAtomicAndConcurrentRetryDoesNotOverwrite()throws Exception{
  emptyBootstrapFixture();String name=newUsername();assertThrows(IllegalArgumentException.class,()->BootstrapAdministrator.initializeForTest(dataSource,passwords,name,"x".repeat(11)));assertEquals(0,jdbc.queryForObject("select count(*) from public.sys_user",Integer.class));String p1=UUID.randomUUID().toString(),p2=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try{var a=pool.submit(()->{start.await();return BootstrapAdministrator.initializeForTest(dataSource,passwords,name,p1);});var b=pool.submit(()->{start.await();return BootstrapAdministrator.initializeForTest(dataSource,passwords,name,p2);});start.countDown();var states=new TreeSet<>(List.of(a.get(20,TimeUnit.SECONDS).state(),b.get(20,TimeUnit.SECONDS).state()));assertEquals(Set.of("INITIALIZED","ALREADY_INITIALIZED"),states);}finally{pool.shutdownNow();}assertEquals(1,jdbc.queryForObject("select count(*) from public.sys_user",Integer.class));String hash=jdbc.queryForObject("select password_hash from public.sys_user",String.class);assertNotEquals(passwords.matches(p1,hash),passwords.matches(p2,hash));assertEquals(1,managerCount());
 }
 @Test void bootstrapRefusesExistingAccountsWithoutMarker()throws Exception{
  String hash=jdbc.queryForObject("select password_hash from public.sys_user where id=?",String.class,adminId);jdbc.update("update public.system_guard set initialized_user_id=null");assertThrows(IllegalStateException.class,()->BootstrapAdministrator.initializeForTest(dataSource,passwords,newUsername(),password));assertEquals(hash,jdbc.queryForObject("select password_hash from public.sys_user where id=?",String.class,adminId));assertEquals(1,jdbc.queryForObject("select count(*) from public.sys_user",Integer.class));
 }
 @Test void corsSupportsActualV2WritesWithoutChangingLegacyCors()throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/profile")).header("Origin","http://localhost:5173").header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","authorization,content-type,x-request-id").method("OPTIONS",HttpRequest.BodyPublishers.noBody());var response=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());REQUESTS.incrementAndGet();assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().contains("PATCH"));
 }
}
