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
class PrivateImageHttpTest {
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
 private static final AtomicInteger REQUESTS=new AtomicInteger();
 private static final AtomicInteger PORT=new AtomicInteger();
 private static final String SECRET=random();
 private static String random(){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
 @DynamicPropertySource static void env(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test_images");r.add("V2_DB_USER",()->System.getenv().getOrDefault("V2_DB_USER","sota"));r.add("V2_DB_PASSWORD",()->System.getenv().getOrDefault("V2_DB_PASSWORD",""));r.add("V2_JWT_SECRET",()->SECRET);}
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
   jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{V2DatabaseTarget.requireTestReset(c);if(!c.getCatalog().equals("dorm_repair_v2_test_images"))throw new IllegalStateException("Wrong fixture database");return null;});
   jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);
   jdbc.execute("truncate public.draft_revision_image,public.private_image,public.repair_draft_revision,public.repair_draft,public.user_address,public.room_resident restart identity cascade");jdbc.execute("truncate public.facility,public.facility_type_repair_item,public.facility_type,public.repair_item,public.room,public.building_floor,public.building,public.campus restart identity cascade");jdbc.update("delete from public.repair_category where parent_id is not null");jdbc.update("update public.repair_category set enabled=true,version=0");jdbc.execute("truncate public.audit_event");jdbc.update("update public.system_guard set initialized_user_id=null,version=0");
   jdbc.update("delete from public.repair_record");jdbc.update("delete from public.repair_order_legacy");jdbc.update("delete from public.repair_order");jdbc.update("delete from public.sys_user_role");jdbc.update("delete from public.sys_user");
   jdbc.update("delete from public.sys_role_permission");jdbc.update("delete from public.sys_role where code not in ('STUDENT','REPAIRER','ADMIN')");jdbc.update("update public.sys_role set enabled=true,version=0");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code='ADMIN'");
   jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code in ('STUDENT','REPAIRER') and (p.code in ('profile:read','profile:write','profile:password') or (r.code='STUDENT' and p.code='legacy:student') or (r.code='REPAIRER' and p.code='legacy:repairer'))");return null;
  });
  password=UUID.randomUUID().toString();adminName=newUsername();var result=BootstrapAdministrator.initializeForTest(dataSource,passwords,adminName,password);adminId=Long.parseLong(result.userId());admin=login(adminName,password);
 }
 @AfterAll static void summary()throws Exception{java.nio.file.Files.writeString(java.nio.file.Path.of("target/V2.0-E-http-summary.json"),"{\"transport\":\"real HTTP and PostgreSQL\",\"requests\":"+REQUESTS.get()+",\"port\":"+PORT.get()+",\"database\":\"dorm_repair_v2_test_images\"}");}

 JsonNode create(String kind,Map<String,Object> body)throws Exception{return call("POST","/api/v2/catalog/admin/"+kind,admin,body,200);}
 String root(){return jdbc.queryForObject("select id::text from public.repair_category where code='WATER'",String.class);}
 JsonNode category(String name,String parent)throws Exception{return create("categories",Map.of("code",name,"name",name,"parentId",parent));}
 JsonNode item(String name)throws Exception{return create("items",Map.of("code",name,"name",name,"categoryId",root(),"estimatedMinutes",30,"isQuick",true));}
 JsonNode campus()throws Exception{return create("campuses",Map.of("code",newUsername(),"name",newUsername()));}
 JsonNode building(String campus)throws Exception{return create("buildings",Map.of("code",newUsername(),"name",newUsername(),"campusId",campus));}
 record Asset(String id,String payload){}
 Asset asset()throws Exception{var c=campus();var b=building(c.get("id").asText());var t=create("facility-types",Map.of("code",newUsername(),"name","Synthetic tap"));var i=item(newUsername());call("PUT","/api/v2/catalog/admin/facility-types/"+t.get("id").asText()+"/items",admin,Map.of("version",0,"itemIds",List.of(i.get("id").asText())),200);var f=create("facilities",Map.of("assetNo",newUsername(),"name","Synthetic public tap","facilityTypeId",t.get("id").asText(),"buildingId",b.get("id").asText(),"publicZone","Synthetic lobby"));String id=f.get("id").asText();return new Asset(id,call("GET","/api/v2/catalog/admin/facilities/"+id+"/qr",admin,null,200).get("payload").asText());}

 Account student()throws Exception{return createUser(List.of(createRole(Set.of("address:own","residency:own","draft:own","facility:read","category:read","space:read"))),"STUDENT");}
 JsonNode room(String kind)throws Exception{var c=campus();var b=building(c.get("id").asText());var fl=create("floors",Map.of("buildingId",b.get("id").asText(),"floorNo",1,"name","Synthetic floor"));return create("rooms",Map.of("floorId",fl.get("id").asText(),"roomNo",newUsername(),"name","Synthetic room","kind",kind));}
 JsonNode address(Account u,String room)throws Exception{return call("POST","/api/v2/addresses",u.token(),Map.of("roomId",room,"contactName","Synthetic contact","phone","00000000000"),200);}
 JsonNode grant(Account u,String room,String from,String to)throws Exception{return call("POST","/api/v2/residencies",admin,Map.of("userId",Long.toString(u.id()),"roomId",room,"validFrom",from,"validTo",to),200);}
 JsonNode grant(Account u,String room)throws Exception{return grant(u,room,java.time.OffsetDateTime.now().minusDays(1).toString(),java.time.OffsetDateTime.now().plusDays(1).toString());}
 String baseAddress(){return "/api/v2/addresses";}

 @Autowired com.dormrepair.v2.images.PrivateImageService imageService;
 java.nio.file.Path imageDirectory(){return (java.nio.file.Path)org.springframework.test.util.ReflectionTestUtils.getField((Object)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(imageService),"root");}
 long fileCount()throws Exception{try(var paths=java.nio.file.Files.list(imageDirectory())){return paths.count();}}
 byte[] fixture(String ext)throws Exception{return new org.springframework.core.io.ClassPathResource("images/synthetic."+ext).getInputStream().readAllBytes();}
 JsonNode upload(String token,byte[] bytes,String filename,int status)throws Exception{String boundary="SyntheticBoundary"+UUID.randomUUID().toString().replace("-","");var out=new java.io.ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/images")).header("Content-Type","multipart/form-data; boundary="+boundary);if(token!=null)builder.header("Authorization","Bearer "+token);var response=HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());assertEquals(status,response.statusCode());return json.readTree(response.body());}
 byte[] content(String token,String id,int status)throws Exception{var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/images/"+id+"/content")).header("Authorization","Bearer "+token).build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(status,response.statusCode());assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());return response.body();}
 @Test void realFormatsAreDecodedAndPrivateMetadataRemoved()throws Exception{var u=student();for(String format:List.of("jpg","png","webp")){var image=upload(u.token(),fixture(format),"../../synthetic."+format,200);byte[] clean=content(u.token(),image.get("id").asText(),200);var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(clean));assertEquals(24,decoded.getWidth());assertEquals(18,decoded.getHeight());assertFalse(new String(clean,java.nio.charset.StandardCharsets.ISO_8859_1).contains("PRIVATE_METADATA_MARKER"));assertFalse(image.toString().contains("storageKey"));assertFalse(image.toString().contains("sha256"));}}
 @Test void spoofedOversizedAndExcessiveDimensionsAreRejected()throws Exception{var u=student();upload(u.token(),"<html>not an image</html>".getBytes(),"fake.jpg",400);upload(u.token(),Arrays.copyOf(fixture("png"),5242880),"exact.png",200);upload(u.token(),Arrays.copyOf(fixture("png"),5242881),"big.png",400);var image=new java.awt.image.BufferedImage(6001,1,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);upload(u.token(),out.toByteArray(),"wide.png",400);upload(null,fixture("png"),"safe.png",401);var low=createUser(List.of(createRole(Set.of("profile:read"))),"REPAIRER");upload(low.token(),fixture("png"),"safe.png",403);}
 @Test void ownershipAndCancelStagedImages()throws Exception{var a=student();var b=student();String id=upload(a.token(),fixture("png"),"safe.png",200).get("id").asText();call("GET","/api/v2/images/"+id,b.token(),null,404);call("GET","/api/v2/images/"+id,admin,null,404);call("GET","/api/v2/images/"+id+"/content",b.token(),null,404);call("GET","/api/v2/images/"+id+"/content",admin,null,404);call("POST","/api/v2/images/"+id+"/cancel",b.token(),null,404);call("POST","/api/v2/images/"+id+"/cancel",a.token(),null,200);call("GET","/api/v2/images/"+id,a.token(),null,404);}
 @Test void revisionBindingsAreImmutableAndOmissionPreservesImages()throws Exception{var u=student();String image=upload(u.token(),fixture("png"),"safe.png",200).get("id").asText();var d=call("POST","/api/v2/drafts",u.token(),Map.of("imageIds",List.of(image)),200);String path="/api/v2/drafts/"+d.get("id").asText();call("PUT",path,u.token(),Map.of("version",1,"description","Changed"),200);assertEquals(1,call("GET",path+"/revisions/2/images",u.token(),null,200).size());call("PUT",path,u.token(),Map.of("version",2,"imageIds",List.of()),200);assertEquals(0,call("GET",path+"/revisions/3/images",u.token(),null,200).size());assertEquals(1,call("GET",path+"/revisions/1/images",u.token(),null,200).size());call("POST","/api/v2/images/"+image+"/cancel",u.token(),null,409);content(u.token(),image,200);call("GET",path+"/revisions/99/images",u.token(),null,404);assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from public.draft_revision_image where image_id=?",Long.parseLong(image)));}
 @Test void sixLimitCrossOwnerCrossDraftAndRollback()throws Exception{var a=student();var b=student();var ids=new ArrayList<String>();for(int n=0;n<7;n++)ids.add(upload(a.token(),fixture("png"),"safe.png",200).get("id").asText());var d=call("POST","/api/v2/drafts",a.token(),Map.of("imageIds",ids.subList(0,6)),200);String path="/api/v2/drafts/"+d.get("id").asText();call("PUT",path,a.token(),Map.of("version",1,"imageIds",ids),400);assertEquals(1,call("GET",path+"/revisions",a.token(),null,200).get("total").asInt());call("POST","/api/v2/drafts",b.token(),Map.of("imageIds",List.of(ids.get(6))),404);call("POST","/api/v2/drafts",a.token(),Map.of("imageIds",List.of(ids.get(0))),409);doThrow(new IllegalStateException("Injected")).when(auditTarget).record(any(),eq("DRAFT_REVISION_SAVED"),anyString(),any(),anyMap());call("PUT",path,a.token(),Map.of("version",1,"imageIds",List.of(ids.get(6))),500);assertEquals("STAGED",jdbc.queryForObject("select state from public.private_image where id=?",String.class,Long.parseLong(ids.get(6))));}
 @Test void uploadFailureCompensatesAndCleanupKeepsBoundHistory()throws Exception{var u=student();long before=jdbc.queryForObject("select count(*) from public.private_image",Long.class);long filesBefore=fileCount();doThrow(new IllegalStateException("Injected")).when(auditTarget).record(any(),eq("IMAGE_UPLOADED"),anyString(),any(),anyMap());upload(u.token(),fixture("png"),"safe.png",500);assertEquals(before,jdbc.queryForObject("select count(*) from public.private_image",Long.class));assertEquals(filesBefore,fileCount());reset(auditTarget);String bound=upload(u.token(),fixture("png"),"safe.png",200).get("id").asText();String staged=upload(u.token(),fixture("png"),"safe.png",200).get("id").asText();call("POST","/api/v2/drafts",u.token(),Map.of("imageIds",List.of(bound)),200);var oldOrphan=imageDirectory().resolve(UUID.randomUUID().toString().replace("-","")+".jpg");var youngOrphan=imageDirectory().resolve(UUID.randomUUID().toString().replace("-","")+".jpg");java.nio.file.Files.write(oldOrphan,new byte[]{1});java.nio.file.Files.write(youngOrphan,new byte[]{1});java.nio.file.Files.setLastModifiedTime(oldOrphan,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()-172800000L));String stagedKey=jdbc.queryForObject("select storage_key from public.private_image where id=?",String.class,Long.parseLong(staged));jdbc.update("update public.private_image set created_at=current_timestamp-interval '2 days'");call("POST","/api/v2/images/cleanup",u.token(),null,403);doThrow(new IllegalStateException("Injected cleanup failure")).when(auditTarget).record(any(),eq("IMAGE_CLEANUP"),anyString(),isNull(),anyMap());call("POST","/api/v2/images/cleanup",admin,null,500);assertEquals("STAGED",jdbc.queryForObject("select state from public.private_image where id=?",String.class,Long.parseLong(staged)));assertTrue(java.nio.file.Files.exists(oldOrphan));reset(auditTarget);call("POST","/api/v2/images/cleanup",admin,null,200);assertEquals("BOUND",jdbc.queryForObject("select state from public.private_image where id=?",String.class,Long.parseLong(bound)));assertEquals("CANCELED",jdbc.queryForObject("select state from public.private_image where id=?",String.class,Long.parseLong(staged)));assertFalse(java.nio.file.Files.exists(imageDirectory().resolve(stagedKey)));assertFalse(java.nio.file.Files.exists(oldOrphan));assertTrue(java.nio.file.Files.exists(youngOrphan));content(u.token(),bound,200);}
}
