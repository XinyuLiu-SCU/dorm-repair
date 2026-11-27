package com.dormrepair.v2.identity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import com.dormrepair.v2.platform.migration.V2DatabaseTarget;
/** No HTTP endpoint. Explicit development-only bootstrap with a durable non-overwrite marker. */
public final class BootstrapAdministrator {
 private BootstrapAdministrator(){}
 public record Result(String state,String userId){}
 public static Result initialize(DataSource ds,PasswordEncoder encoder,String username,String password){return run(ds,encoder,username,password,false);}
 // Package-private verification entry; never selectable by the development CLI or an HTTP parameter.
 static Result initializeForTest(DataSource ds,PasswordEncoder encoder,String username,String password){return run(ds,encoder,username,password,true);}
 private static Result run(DataSource ds,PasswordEncoder encoder,String username,String password,boolean test){
  var jdbc=new JdbcTemplate(ds);
  return new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(status->{
   jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{try{V2DatabaseTarget.verify(c);if(test)V2DatabaseTarget.requireTestReset(c);else if(!c.getCatalog().equals("dorm_repair_v2_dev"))throw new IllegalStateException("Bootstrap is development-only");}catch(java.sql.SQLException e){throw new IllegalStateException("Cannot verify bootstrap target",e);}return null;});
   if(!jdbc.queryForObject("select to_regclass('v2_business_history.flyway_schema_history') is not null",Boolean.class) || jdbc.queryForObject("select count(*) from v2_business_history.flyway_schema_history where version='3' and success",Long.class)!=1)throw new IllegalStateException("Apply the guarded V3 migration before bootstrap");
   if(username==null||!username.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{2,49}"))throw new IllegalArgumentException("Invalid synthetic username");
   jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);
   Long existing=jdbc.queryForObject("select initialized_user_id from public.system_guard where guard_key='rbac-admin'",Long.class);
   if(existing!=null){String original=jdbc.queryForObject("select username from public.sys_user where id=?",String.class,existing);if(!username.equals(original))throw new IllegalStateException("Administrator already initialized; no changes made");return new Result("ALREADY_INITIALIZED",existing.toString());}
   if(jdbc.queryForObject("select count(*) from public.sys_user",Long.class)!=0)throw new IllegalStateException("Bootstrap requires an empty V2 user table; existing accounts are never overwritten");
   if(password==null||password.codePointCount(0,password.length())<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Password requires at least 12 characters and at most 72 UTF8 bytes");
   Long role=jdbc.queryForObject("select id from public.sys_role where code='ADMIN' and enabled",Long.class);
   long grants=jdbc.queryForObject("select count(*) from public.sys_role_permission where role_id=?",Long.class,role);
   if(grants!=jdbc.queryForObject("select count(*) from public.sys_permission where available",Long.class))throw new IllegalStateException("Initial administrator seed is incomplete");
   Long id=jdbc.queryForObject("insert into public.sys_user(username,password_hash,real_name,role) values (?,?,?,'ADMIN') returning id",Long.class,username,encoder.encode(password),"合成开发管理员");
   if(new RbacRepository(jdbc).managers()!=1)throw new IllegalStateException("Bootstrap did not produce an effective permission custodian");
   jdbc.update("update public.system_guard set initialized_user_id=?,version=version+1 where guard_key='rbac-admin'",id);
   jdbc.update("insert into public.audit_event(actor_id,action,target_type,target_id,request_id,details) values (?,'ADMIN_INITIALIZED','USER',?,?,?::jsonb)",id,id,UUID.randomUUID().toString(),"{\"source\":\"explicit-dev-bootstrap\"}");
   return new Result("INITIALIZED",id.toString());
  });
 }
}
