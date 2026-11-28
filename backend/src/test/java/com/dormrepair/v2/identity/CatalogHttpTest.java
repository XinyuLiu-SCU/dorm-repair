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

@org.springframework.context.annotation.Import(CatalogSqlCounterConfiguration.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("v2-test")
@EnabledIfSystemProperty(named="flowable.v2B",matches="true")
@DirtiesContext
class CatalogHttpTest {
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
 private static final AtomicInteger REQUESTS=new AtomicInteger();
 private static final AtomicInteger PORT=new AtomicInteger();
 private static final String SECRET=random();
 private static String random(){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
 @DynamicPropertySource static void env(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test_qualityd");r.add("V2_DB_USER",()->System.getenv().getOrDefault("V2_DB_USER","sota"));r.add("V2_DB_PASSWORD",()->System.getenv().getOrDefault("V2_DB_PASSWORD",""));r.add("V2_JWT_SECRET",()->SECRET);}
 @LocalServerPort int port;
 @MockitoSpyBean JdbcTemplate jdbc;
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
   jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{V2DatabaseTarget.requireTestReset(c);if(!c.getCatalog().equals("dorm_repair_v2_test_qualityd"))throw new IllegalStateException("Wrong fixture database");return null;});
   jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);
   jdbc.execute("truncate public.facility,public.facility_type_repair_item,public.facility_type,public.repair_item,public.room,public.building_floor,public.building,public.campus restart identity cascade");jdbc.update("delete from public.repair_category where parent_id is not null");jdbc.update("update public.repair_category set enabled=true,version=0");jdbc.execute("truncate public.audit_event");jdbc.update("update public.system_guard set initialized_user_id=null,version=0");
   jdbc.update("delete from public.repair_record");jdbc.update("delete from public.repair_order_legacy");jdbc.update("delete from public.repair_order");jdbc.update("delete from public.sys_user_role");jdbc.update("delete from public.sys_user");
   jdbc.update("delete from public.sys_role_permission");jdbc.update("delete from public.sys_role where code not in ('STUDENT','REPAIRER','ADMIN')");jdbc.update("update public.sys_role set enabled=true,version=0");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code='ADMIN'");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code in ('STUDENT','REPAIRER') and (p.code in ('profile:read','profile:write','profile:password') or (r.code='STUDENT' and p.code='legacy:student') or (r.code='REPAIRER' and p.code='legacy:repairer'))");return null;
  });
  password=UUID.randomUUID().toString();adminName=newUsername();var result=BootstrapAdministrator.initializeForTest(dataSource,passwords,adminName,password);adminId=Long.parseLong(result.userId());admin=login(adminName,password);
 }
 @AfterAll static void summary()throws Exception{java.nio.file.Files.writeString(java.nio.file.Path.of("target/V2.0-C-http-summary.json"),"{\"transport\":\"real HTTP and PostgreSQL\",\"requests\":"+REQUESTS.get()+",\"port\":"+PORT.get()+",\"database\":\"dorm_repair_v2_test_qualityd\"}");}

 JsonNode create(String kind,Map<String,Object> body)throws Exception{return call("POST","/api/v2/catalog/admin/"+kind,admin,body,200);}
 String root(){return jdbc.queryForObject("select id::text from public.repair_category where code='WATER'",String.class);}
 JsonNode category(String name,String parent)throws Exception{return create("categories",Map.of("code",name,"name",name,"parentId",parent));}
 JsonNode item(String name)throws Exception{return create("items",Map.of("code",name,"name",name,"categoryId",root(),"estimatedMinutes",30,"isQuick",true));}
 JsonNode campus()throws Exception{return create("campuses",Map.of("code",newUsername(),"name",newUsername()));}
 JsonNode building(String campus)throws Exception{return create("buildings",Map.of("code",newUsername(),"name",newUsername(),"campusId",campus));}
 record Asset(String id,String payload){}
 Asset asset()throws Exception{var c=campus();var b=building(c.get("id").asText());var t=create("facility-types",Map.of("code",newUsername(),"name","Synthetic tap"));var i=item(newUsername());call("PUT","/api/v2/catalog/admin/facility-types/"+t.get("id").asText()+"/items",admin,Map.of("version",0,"itemIds",List.of(i.get("id").asText())),200);var f=create("facilities",Map.of("assetNo",newUsername(),"name","Synthetic public tap","facilityTypeId",t.get("id").asText(),"buildingId",b.get("id").asText(),"publicZone","Synthetic lobby"));String id=f.get("id").asText();return new Asset(id,call("GET","/api/v2/catalog/admin/facilities/"+id+"/qr",admin,null,200).get("payload").asText());}
 @Test void categoryDepthCycleAndRootRules()throws Exception{var child=category(newUsername(),root());var leaf=category(newUsername(),child.get("id").asText());call("POST","/api/v2/catalog/admin/categories",admin,Map.of("code",newUsername(),"name","too deep","parentId",leaf.get("id").asText()),409);call("PATCH","/api/v2/catalog/admin/categories/"+child.get("id").asText(),admin,Map.of("version",0,"parentId",leaf.get("id").asText()),409);call("POST","/api/v2/catalog/admin/categories",admin,Map.of("code",newUsername(),"name","ninth"),409);assertEquals(8,call("GET","/api/v2/catalog/select/categories",admin,null,200).size());}
 @Test void disabledAncestorRemovesItemsAndQuickEntries()throws Exception{item(newUsername());assertEquals(1,call("GET","/api/v2/catalog/select/items?quick=true",admin,null,200).size());call("PATCH","/api/v2/catalog/admin/categories/"+root(),admin,Map.of("version",0,"enabled",false),200);assertEquals(0,call("GET","/api/v2/catalog/select/items?quick=true",admin,null,200).size());}
 @Test void locationsRejectCrossBuildingAndDuplicateCodes()throws Exception{var c=campus();var b=building(c.get("id").asText());var b2=building(c.get("id").asText());var fl=create("floors",Map.of("buildingId",b.get("id").asText(),"floorNo",1,"name","First"));call("POST","/api/v2/catalog/admin/floors",admin,Map.of("buildingId",b.get("id").asText(),"floorNo",1,"name","Duplicate"),409);var t=create("facility-types",Map.of("code",newUsername(),"name","Type"));call("POST","/api/v2/catalog/admin/facilities",admin,Map.of("assetNo",newUsername(),"name","Bad location","facilityTypeId",t.get("id").asText(),"buildingId",b2.get("id").asText(),"floorId",fl.get("id").asText(),"publicZone","Public"),409);assertEquals(1,call("GET","/api/v2/catalog/select/floors?parentId="+b.get("id").asText(),admin,null,200).size());}
 @Test void versionConflictAndAuditAreAtomic()throws Exception{var c=campus();long before=jdbc.queryForObject("select count(*) from public.audit_event",Long.class);call("PATCH","/api/v2/catalog/admin/campuses/"+c.get("id").asText(),admin,Map.of("version",0,"name","Updated"),200);call("PATCH","/api/v2/catalog/admin/campuses/"+c.get("id").asText(),admin,Map.of("version",0,"name","Stale"),409);assertEquals(before+1,jdbc.queryForObject("select count(*) from public.audit_event",Long.class));}
 @Test void auditFailureRollsBackCatalog()throws Exception{doThrow(new IllegalStateException("Injected")).when(auditTarget).record(any(),eq("CATALOG_CREATED"),anyString(),any(),anyMap());long before=jdbc.queryForObject("select count(*) from public.campus",Long.class);call("POST","/api/v2/catalog/admin/campuses",admin,Map.of("code",newUsername(),"name","Rollback"),500);assertEquals(before,jdbc.queryForObject("select count(*) from public.campus",Long.class));}
 @Test void publicQrPayloadResolvesWithoutAssetSecrets()throws Exception{var a=asset();var out=call("GET","/api/v2/facilities/resolve/"+a.payload().substring(a.payload().lastIndexOf('/')+1),admin,null,200);assertEquals(1,out.get("items").size());assertFalse(out.toString().contains("assetNo"));assertFalse(out.toString().contains("qrToken"));assertFalse(out.get("canSubmitOrder").asBoolean());call("PATCH","/api/v2/catalog/admin/facilities/"+a.id(),admin,Map.of("version",0,"status","DISABLED"),200);call("GET","/api/v2/facilities/resolve/"+a.payload().substring(a.payload().lastIndexOf('/')+1),admin,null,404);}
 @Test void realQrPngCanBeDecoded()throws Exception{var a=asset();var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/catalog/admin/facilities/"+a.id()+"/qr.png")).header("Authorization","Bearer "+admin).build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,response.statusCode());var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body()));int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels)));assertEquals(a.payload(),new com.google.zxing.MultiFormatReader().decode(bitmap).getText());}
 @Test void readonlyPermissionCannotMutateOrReadAssetArchive()throws Exception{var role=createRole(Set.of("category:read","space:read","facility:read"));var u=createUser(List.of(role),"STUDENT");call("GET","/api/v2/catalog/select/categories",u.token(),null,200);call("GET","/api/v2/catalog/admin/categories",u.token(),null,403);call("POST","/api/v2/catalog/admin/campuses",u.token(),Map.of("code","BAD","name","Bad"),403);call("GET","/api/v2/facilities",null,null,401);}
 @Test void concurrentUpdatesHaveOneWinner()throws Exception{var c=campus();try(var pool=Executors.newFixedThreadPool(2)){var first=pool.submit(()->raw("PATCH","/api/v2/catalog/admin/campuses/"+c.get("id").asText(),admin,Map.of("version",0,"name","First")));var second=pool.submit(()->raw("PATCH","/api/v2/catalog/admin/campuses/"+c.get("id").asText(),admin,Map.of("version",0,"name","Second")));assertEquals(Set.of(200,409),Set.of(first.get().status(),second.get().status()));}}
 @Test void paginationAndEstimatedMinutesRejectInvalidInput()throws Exception{call("GET","/api/v2/catalog/admin/items?size=101",admin,null,400);call("POST","/api/v2/catalog/admin/items",admin,Map.of("code",newUsername(),"name","Bad","categoryId",root(),"estimatedMinutes",0),409);call("GET","/api/v2/catalog/select/rooms",admin,null,400);}
 @Test void treeShowsOnlyEnabledBranches()throws Exception{var c=category(newUsername(),root());assertEquals(8,call("GET","/api/v2/catalog/tree",admin,null,200).size());call("PATCH","/api/v2/catalog/admin/categories/"+c.get("id").asText(),admin,Map.of("version",0,"enabled",false),200);for(var node:call("GET","/api/v2/catalog/tree",admin,null,200))if(node.get("id").asText().equals(root()))assertEquals(0,node.get("children").size());}
 @Test void concurrentCrossMovesCannotCreateCycle()throws Exception{var a=category(newUsername(),root());var b=category(newUsername(),root());try(var pool=Executors.newFixedThreadPool(2)){var x=pool.submit(()->raw("PATCH","/api/v2/catalog/admin/categories/"+a.get("id").asText(),admin,Map.of("version",0,"parentId",b.get("id").asText())));var y=pool.submit(()->raw("PATCH","/api/v2/catalog/admin/categories/"+b.get("id").asText(),admin,Map.of("version",0,"parentId",a.get("id").asText())));assertEquals(Set.of(200,409),Set.of(x.get().status(),y.get().status()));}}

 @Test void associationEchoPreservesDisabledItemsAndClears()throws Exception{var t=create("facility-types",Map.of("code",newUsername(),"name","Type"));var it=item(newUsername());String path="/api/v2/catalog/admin/facility-types/"+t.get("id").asText();call("PUT",path+"/items",admin,Map.of("version",0,"itemIds",List.of(it.get("id").asText())),200);call("PATCH","/api/v2/catalog/admin/items/"+it.get("id").asText(),admin,Map.of("version",0,"enabled",false),200);var echo=call("GET",path,admin,null,200);assertEquals(1,echo.get("itemIds").size());assertFalse(echo.get("items").get(0).get("enabled").asBoolean());assertEquals(1,echo.get("version").asInt());call("PUT",path+"/items",admin,Map.of("version",0,"itemIds",List.of()),409);call("PUT",path+"/items",admin,Map.of("version",1,"itemIds",List.of()),200);assertEquals(0,call("GET",path,admin,null,200).get("items").size());}
 @Test void privateFacilitiesInvisibleToStudentRepairerAndReadOnlyAdmin()throws Exception{var a=asset();long id=Long.parseLong(a.id());long b=jdbc.queryForObject("select building_id from public.facility where id=?",Long.class,id);var floor=create("floors",Map.of("buildingId",Long.toString(b),"floorNo",1,"name","Private floor"));var room=create("rooms",Map.of("floorId",floor.get("id").asText(),"roomNo","PRIVATE","name","Private room","kind","PRIVATE"));jdbc.update("update public.facility set floor_id=?,room_id=?,public_zone=null where id=?",Long.parseLong(floor.get("id").asText()),Long.parseLong(room.get("id").asText()),id);long role=createRole(Set.of("facility:read","space:read"));for(String legacy:List.of("STUDENT","REPAIRER","ADMIN")){var u=createUser(List.of(role),legacy);assertEquals(0,call("GET","/api/v2/facilities",u.token(),null,200).get("total").asInt());call("GET","/api/v2/facilities/resolve/"+a.payload().substring(a.payload().lastIndexOf('/')+1),u.token(),null,404);call("GET","/api/v2/catalog/admin/facilities/"+id,u.token(),null,403);}call("GET","/api/v2/catalog/admin/facilities/"+id,admin,null,200);call("GET","/api/v2/facilities/resolve/"+a.payload().substring(a.payload().lastIndexOf('/')+1),admin,null,404);}
 @Test void publicRoomFacilitiesRemainVisibleAndAncestorDisableHidesThem()throws Exception{var a=asset();long b=jdbc.queryForObject("select building_id from public.facility where id=?",Long.class,Long.parseLong(a.id()));var floor=create("floors",Map.of("buildingId",Long.toString(b),"floorNo",1,"name","Public floor"));var room=create("rooms",Map.of("floorId",floor.get("id").asText(),"roomNo","PUBLIC","name","Public room","kind","PUBLIC"));jdbc.update("update public.facility set floor_id=?,room_id=?,public_zone=null where id=?",Long.parseLong(floor.get("id").asText()),Long.parseLong(room.get("id").asText()),Long.parseLong(a.id()));assertEquals(1,call("GET","/api/v2/facilities",admin,null,200).get("total").asInt());call("PATCH","/api/v2/catalog/admin/floors/"+floor.get("id").asText(),admin,Map.of("version",0,"enabled",false),200);assertEquals(0,call("GET","/api/v2/facilities",admin,null,200).get("total").asInt());}

 long queryCount(){return CatalogSqlCounterConfiguration.COUNT.get();}
 @Test void queryCountsStayBoundedAtTenFiftyHundred()throws Exception{var result=new ArrayList<Map<String,Object>>();var endpointResults=new ArrayList<Map<String,Object>>();Long optimized=null;for(int count:List.of(10,50,100)){jdbc.update("delete from public.facility");var a=asset();long original=Long.parseLong(a.id());jdbc.update("insert into public.facility(asset_no,name,facility_type_id,building_id,public_zone,status,qr_token,qr_token_hash) select 'SYNTHETIC_PERF_'||g,'Synthetic performance',facility_type_id,building_id,public_zone,status,md5(g::text)||md5((g+1000)::text),md5((g+2000)::text)||md5((g+3000)::text) from public.facility cross join generate_series(1,?) g where id=?",count-1,original);
 CatalogSqlCounterConfiguration.COUNT.set(0);long start=System.nanoTime();var rows=jdbc.queryForList("select * from public.facility order by id limit ?",count);for(var row:rows){long type=((Number)row.get("facility_type_id")).longValue();jdbc.queryForList("select * from public.facility_type where id=?",type);var b=jdbc.queryForMap("select * from public.building where id=?",row.get("building_id"));jdbc.queryForList("select * from public.campus where id=?",b.get("campus_id"));var items=jdbc.queryForList("select i.* from public.repair_item i join public.facility_type_repair_item t on t.item_id=i.id where t.type_id=?",type);for(var item:items)jdbc.queryForList("select * from public.repair_category where id=?",item.get("category_id"));}jdbc.queryForObject("select count(*) from public.facility",Long.class);long before=queryCount();double oldMs=(System.nanoTime()-start)/1e6;
 CatalogSqlCounterConfiguration.COUNT.set(0);start=System.nanoTime();assertEquals(count,call("GET","/api/v2/facilities?size="+count,admin,null,200).get("items").size());long after=queryCount();double newMs=(System.nanoTime()-start)/1e6;if(optimized==null)optimized=after;else assertEquals(optimized.longValue(),after);assertTrue(before>after);result.add(Map.of("records",count,"beforeSql",before,"afterSql",after,"beforeMs",oldMs,"afterMs",newMs));var category=category(newUsername(),root());jdbc.update("insert into public.repair_item(code,name,category_id,estimated_minutes) select 'SYNTHETIC_COUNT_'||?||'_'||g,'Synthetic counted '||?||'_'||g,?,30 from generate_series(1,?) g",count,count,Long.parseLong(category.get("id").asText()),count);for(String endpoint:List.of("/api/v2/catalog/tree","/api/v2/catalog/select/items?parentId="+category.get("id").asText(),"/api/v2/catalog/select/buildings?parentId="+jdbc.queryForObject("select campus_id from public.building where id=(select building_id from public.facility limit 1)",Long.class))){CatalogSqlCounterConfiguration.COUNT.set(0);long clock=System.nanoTime();call("GET",endpoint,admin,null,200);endpointResults.add(Map.of("records",count,"endpoint",endpoint.replaceAll("parentId=[0-9]+","parentId=ID"),"sql",queryCount(),"ms",(System.nanoTime()-clock)/1e6));}}
 java.nio.file.Files.writeString(java.nio.file.Path.of("target/V2.0-C-query-counts.json"),json.writeValueAsString(result));java.nio.file.Files.writeString(java.nio.file.Path.of("target/V2.0-C-endpoint-counts.json"),json.writeValueAsString(endpointResults));}

 @Test void migrationRevocationIsEnforcedByRealHttp()throws Exception{long shared=createRole(Set.of("facility:read"));var low=createUser(List.of(shared),"STUDENT");jdbc.update("insert into public.sys_role_permission select ?,id from public.sys_permission where code='facility:write'",shared);call("GET","/api/v2/catalog/admin/facilities",low.token(),null,200);new TransactionTemplate(transactionManager).execute(status->{jdbc.execute("create temporary table c6_confirmed_grants(role_id bigint,permission_code varchar(80),primary key(role_id,permission_code)) on commit drop");jdbc.update("insert into c6_confirmed_grants select r.id,p.code from public.sys_role r cross join public.sys_permission p where r.code='ADMIN' and p.code in ('category:write','space:write','facility:write')");jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{try(var statement=c.createStatement()){statement.execute(new String(new org.springframework.core.io.ClassPathResource("db/v2/business/V6__guarded_catalog_grants.sql").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new IllegalStateException(e);}return null;});return null;});call("GET","/api/v2/auth/me",low.token(),null,401);call("GET","/api/v2/auth/me",admin,null,401);String fresh=login(low.username(),password);call("GET","/api/v2/catalog/admin/facilities",fresh,null,403);admin=login(adminName,password);assertEquals(1,managerCount());call("GET","/api/v2/catalog/admin/facilities",admin,null,200);}
}
