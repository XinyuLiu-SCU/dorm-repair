package com.dormrepair.v2.platform.migration;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="flowable.v2A",matches="true")
class V2MigrationTest {
 private DataSource ds(String suffix){return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test_"+suffix,System.getenv().getOrDefault("V2_DB_USER","sota"),System.getenv().getOrDefault("V2_DB_PASSWORD",""));}
 private JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
 private Flyway flyway(DataSource ds,String... extra){
  var locations=new ArrayList<String>();locations.add("classpath:db/v2/business");locations.addAll(List.of(extra));
  return Flyway.configure().dataSource(ds).locations(locations.toArray(String[]::new)).defaultSchema("v2_business_history").schemas("v2_business_history")
    .target(extra.length==0?"5":"9000").baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true).validateMigrationNaming(true).group(true).mixed(false).load();
 }
 private void migrate(DataSource ds,Flyway flyway)throws Exception{V2MigrationConfiguration.verifyConfiguration(flyway);try(var c=ds.getConnection()){LegacySchemaVerifier.verifyBeforeFirstMigration(c);}flyway.migrate();}
 private DataSource reset(String suffix)throws Exception {
  assertTrue(Set.of("legacy","failure").contains(suffix));DataSource ds=ds(suffix);
  try(var c=ds.getConnection()){
   V2DatabaseTarget.requireTestReset(c);
   try(var s=c.createStatement()) { s.execute("drop schema if exists v2_business_history cascade");s.execute("drop schema public cascade");s.execute("create schema public"); }
  }return ds;
 }
 private void syntheticV1(DataSource ds)throws Exception{try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("workflow/business-fixture.sql"));ScriptUtils.executeSqlScript(c,new ClassPathResource("workflow/bootstrap-rehearsal.sql"));}}
 private List<String> snapshot(DataSource ds,String table){return jdbc(ds).queryForList("select (to_jsonb(t)-'process_instance_id'-'enabled'-'auth_version'-'version')::text from public."+table+" t order by id",String.class);}
 @Test void emptyDatabaseAndRepeatMigrationCreateNoBusinessData()throws Exception {
  var ds=reset("failure");migrate(ds,flyway(ds));migrate(ds,flyway(ds));
  assertEquals(5,jdbc(ds).queryForObject("select count(*) from v2_business_history.flyway_schema_history where version is not null and success",Integer.class));
  assertEquals(19,jdbc(ds).queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'",Integer.class));
  for(String table:List.of("sys_user","repair_order","repair_record","repair_order_legacy"))assertEquals(0,jdbc(ds).queryForObject("select count(*) from public."+table,Integer.class));
  assertEquals(0,jdbc(ds).queryForObject("select count(*) from information_schema.tables where table_schema='public' and (table_name like 'act_%' or table_name like 'flw_%')",Integer.class));
 }
 @Test void verifiedUnmigratedV1KeepsAllSyntheticRowsAndSequences()throws Exception {
  var ds=reset("legacy");syntheticV1(ds);var users=snapshot(ds,"sys_user");var orders=snapshot(ds,"repair_order");var records=snapshot(ds,"repair_record");
  Long sequence=jdbc(ds).queryForObject("select last_value from public.repair_order_id_seq",Long.class);
  migrate(ds,flyway(ds));migrate(ds,flyway(ds));
  assertEquals(users,snapshot(ds,"sys_user"));assertEquals(orders,snapshot(ds,"repair_order"));assertEquals(records,snapshot(ds,"repair_record"));
  assertEquals(sequence,jdbc(ds).queryForObject("select last_value from public.repair_order_id_seq",Long.class));
  assertEquals(3,jdbc(ds).queryForObject("select count(*) from public.repair_order_legacy",Integer.class));
  assertEquals(List.of("REJECTED","COMPLETED","COMPLETED"),jdbc(ds).queryForList("select status from public.repair_order order by id",String.class));
 }
 @Test void existingWorkflowSchemaAndEngineTablesAreNotOwnedOrChanged()throws Exception {
  var ds=reset("legacy");syntheticV1(ds);
  try(var c=ds.getConnection()){
   ScriptUtils.executeSqlScript(c,new ClassPathResource("db/manual/B001-repair-workflow.sql"));
   for(String file:List.of("010-common.sql","020-engine.sql","030-history.sql"))ScriptUtils.executeSqlScript(c,new FileSystemResource("deployment/postgres-flowable-7.2.0/"+file));
  }
  var legacyColumns=jdbc(ds).queryForList("select table_name,column_name,ordinal_position,data_type,character_maximum_length,is_nullable,column_default,is_identity from information_schema.columns where table_schema='public' order by table_name,ordinal_position");
  var legacyUsers=snapshot(ds,"sys_user");var legacyOrders=snapshot(ds,"repair_order");var legacyRecords=snapshot(ds,"repair_record");
  var rows=jdbc(ds).queryForList("select * from public.act_ge_property order by name_");
  String schema=jdbc(ds).queryForObject("select md5(string_agg(table_name||column_name||data_type,'|' order by table_name,ordinal_position)) from information_schema.columns where table_schema='public' and (table_name like 'act_%' or table_name like 'flw_%')",String.class);
  migrate(ds,flyway(ds));
  assertTrue(jdbc(ds).queryForList("select table_name,column_name,ordinal_position,data_type,character_maximum_length,is_nullable,column_default,is_identity from information_schema.columns where table_schema='public' order by table_name,ordinal_position").containsAll(legacyColumns));
  assertEquals(legacyUsers,snapshot(ds,"sys_user"));assertEquals(legacyOrders,snapshot(ds,"repair_order"));assertEquals(legacyRecords,snapshot(ds,"repair_record"));
  assertEquals(rows,jdbc(ds).queryForList("select * from public.act_ge_property order by name_"));
  assertEquals(schema,jdbc(ds).queryForObject("select md5(string_agg(table_name||column_name||data_type,'|' order by table_name,ordinal_position)) from information_schema.columns where table_schema='public' and (table_name like 'act_%' or table_name like 'flw_%')",String.class));
  assertEquals(32,jdbc(ds).queryForObject("select count(*) from information_schema.tables where table_schema='public' and (table_name like 'act_%' or table_name like 'flw_%')",Integer.class));
 }
 @Test void partialSchemaRejectedBeforeFlywayHistoryCreation()throws Exception {
  var ds=reset("failure");jdbc(ds).execute("create table public.sys_user(id bigint)");
  assertThrows(IllegalStateException.class,()->migrate(ds,flyway(ds)));
  assertNull(jdbc(ds).queryForObject("select to_regclass('v2_business_history.flyway_schema_history')::text",String.class));
  assertEquals(1,jdbc(ds).queryForObject("select count(*) from information_schema.tables where table_schema='public'",Integer.class));
 }
 @Test void alteredColumnRejectedWithoutSilentBaseline()throws Exception {
  var ds=reset("legacy");syntheticV1(ds);jdbc(ds).execute("alter table public.sys_user alter column username type varchar(49)");
  assertThrows(IllegalStateException.class,()->migrate(ds,flyway(ds)));
  assertNull(jdbc(ds).queryForObject("select to_regclass('v2_business_history.flyway_schema_history')::text",String.class));
  assertEquals(49,jdbc(ds).queryForObject("select character_maximum_length from information_schema.columns where table_schema='public' and table_name='sys_user' and column_name='username'",Integer.class));
 }
 @Test void weakenedEnumConstraintIsNotAdopted()throws Exception {
  var ds=reset("legacy");syntheticV1(ds);jdbc(ds).execute("alter table public.sys_user drop constraint sys_user_role_check");jdbc(ds).execute("alter table public.sys_user add constraint sys_user_role_check check (role in ('STUDENT','REPAIRER','ADMIN') or true)");
  assertThrows(IllegalStateException.class,()->migrate(ds,flyway(ds)));assertNull(jdbc(ds).queryForObject("select to_regclass('v2_business_history.flyway_schema_history')::text",String.class));
 }
 @Test void missingForeignKeyRejected()throws Exception {
  var ds=reset("legacy");syntheticV1(ds);jdbc(ds).execute("alter table public.repair_record drop constraint repair_record_order_id_fkey");
  assertThrows(IllegalStateException.class,()->migrate(ds,flyway(ds)));
  assertEquals(3,jdbc(ds).queryForObject("select count(*) from public.repair_order",Integer.class));
 }
 @Test void transactionFailureRollsBackAllBusinessDdl()throws Exception {
  var ds=reset("failure");assertThrows(org.flywaydb.core.api.FlywayException.class,()->migrate(ds,flyway(ds,"classpath:db/v2/failure")));
  assertEquals(0,jdbc(ds).queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'",Integer.class));
  assertEquals(0,jdbc(ds).queryForObject("select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public'",Integer.class));
  assertEquals(0,jdbc(ds).queryForObject("select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind='S'",Integer.class));
  assertEquals(0,jdbc(ds).queryForObject("select count(*) from v2_business_history.flyway_schema_history where version is not null",Integer.class));
  migrate(ds,flyway(ds));assertEquals(5,jdbc(ds).queryForObject("select count(*) from v2_business_history.flyway_schema_history where version is not null and success",Integer.class));
 }
 @Test void checksumMismatchFailsWithoutRepair(@TempDir Path temp)throws Exception {
  var ds=reset("failure");migrate(ds,flyway(ds));
  for(String name:List.of("V1__verify_or_bootstrap_v1_core.sql","V2__prepare_legacy_workflow_association.sql","V3__rbac_users_and_security_audit.sql","V4__foundation_catalog_and_assets.sql","V5__catalog_uniqueness_guards.sql")) {
   String text=new ClassPathResource("db/v2/business/"+name).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
   Files.writeString(temp.resolve(name),text+(name.startsWith("V1")?"\nselect 42;\n":""));
  }
  var altered=Flyway.configure().dataSource(ds).locations("filesystem:"+temp).defaultSchema("v2_business_history").schemas("v2_business_history").cleanDisabled(true).load();
  assertThrows(org.flywaydb.core.api.FlywayException.class,altered::validate);
  assertEquals(5,jdbc(ds).queryForObject("select count(*) from v2_business_history.flyway_schema_history where version is not null and success",Integer.class));
 }
 @Test void flywayCleanIsDisabledEvenOnTheTestDatabase()throws Exception {
  var ds=reset("failure");migrate(ds,flyway(ds));assertThrows(org.flywaydb.core.api.FlywayException.class,()->flyway(ds).clean());
  assertNotNull(jdbc(ds).queryForObject("select to_regclass('public.sys_user')::text",String.class));
 }
 @Test void v3MapsAllLegacyRolesWithoutChangingPasswordsOrBusinessIds()throws Exception{
  var ds=reset("legacy");syntheticV1(ds);jdbc(ds).update("insert into public.sys_user(id,username,password_hash,real_name,role) values (99,'synthetic_old_admin','placeholder','Synthetic old administrator','ADMIN')");var users=snapshot(ds,"sys_user");var orders=snapshot(ds,"repair_order");var records=snapshot(ds,"repair_record");migrate(ds,flyway(ds));assertEquals(users,snapshot(ds,"sys_user"));assertEquals(orders,snapshot(ds,"repair_order"));assertEquals(records,snapshot(ds,"repair_record"));assertEquals(3,jdbc(ds).queryForObject("select count(*) from public.sys_user u join public.sys_user_role ur on ur.user_id=u.id join public.sys_role r on r.id=ur.role_id where r.code=u.role and u.enabled and u.auth_version=1 and u.version=1",Integer.class));assertEquals(1,new com.dormrepair.v2.identity.RbacRepository(jdbc(ds)).managers());
 }
 @Test void appliedV1V2ChecksumsStayUnchangedWhenV3IsAppended()throws Exception{
  var ds=reset("legacy");syntheticV1(ds);var target=Flyway.configure().configuration(flyway(ds).getConfiguration()).target("2").load();migrate(ds,target);var rows=snapshot(ds,"sys_user");var checksums=jdbc(ds).queryForList("select version,checksum from v2_business_history.flyway_schema_history where version in ('1','2') order by version");migrate(ds,flyway(ds));assertEquals(rows,snapshot(ds,"sys_user"));assertEquals(checksums,jdbc(ds).queryForList("select version,checksum from v2_business_history.flyway_schema_history where version in ('1','2') order by version"));assertEquals(5,jdbc(ds).queryForObject("select count(*) from v2_business_history.flyway_schema_history where version is not null and success",Integer.class));
 }

 @Test void cMigrationPreservesCustomCustodianAndInvalidatesOldTokens()throws Exception{
 var ds=reset("legacy");var b=Flyway.configure().configuration(flyway(ds).getConfiguration()).target("3").load();migrate(ds,b);var j=jdbc(ds);j.update("insert into public.sys_role(code,name) values ('CUSTOM_KEEPER','Synthetic keeper')");long role=j.queryForObject("select id from public.sys_role where code='CUSTOM_KEEPER'",Long.class);j.update("insert into public.sys_role_permission select ?,id from public.sys_permission",role);j.update("insert into public.sys_user(username,password_hash,real_name,role) values ('synthetic_keeper','unchanged-hash','Synthetic keeper','STUDENT')");long user=j.queryForObject("select id from public.sys_user where username='synthetic_keeper'",Long.class);j.update("delete from public.sys_user_role where user_id=?",user);j.update("insert into public.sys_user_role values (?,?)",user,role);var sums=j.queryForList("select version,checksum from v2_business_history.flyway_schema_history where version in ('1','2','3') order by version");migrate(ds,flyway(ds));assertEquals(1,new com.dormrepair.v2.identity.RbacRepository(j).managers());assertEquals(1,j.queryForObject("select auth_version from public.sys_user where id=?",Integer.class,user));assertEquals("unchanged-hash",j.queryForObject("select password_hash from public.sys_user where id=?",String.class,user));assertEquals(16,j.queryForObject("select count(*) from public.sys_role_permission where role_id=?",Integer.class,role));assertEquals(sums,j.queryForList("select version,checksum from v2_business_history.flyway_schema_history where version in ('1','2','3') order by version"));migrate(ds,flyway(ds));assertEquals(1,j.queryForObject("select auth_version from public.sys_user where id=?",Integer.class,user));
 }
}
