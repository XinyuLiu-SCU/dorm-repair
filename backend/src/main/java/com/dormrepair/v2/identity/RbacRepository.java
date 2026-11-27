package com.dormrepair.v2.identity;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class RbacRepository {
 private final JdbcTemplate jdbc;
 public RbacRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record User(long id,String username,String passwordHash,String realName,String phone,String legacyRole,boolean enabled,long authVersion,long version){@Override public String toString(){return "User[id="+id+"]";}}
 public record Role(long id,String code,String name,boolean enabled,long version){}
 private static final String USER_COLUMNS="id,username,password_hash,real_name,phone,role,enabled,auth_version,version";
 private User user(java.sql.ResultSet rs)throws java.sql.SQLException{return new User(rs.getLong("id"),rs.getString("username"),rs.getString("password_hash"),rs.getString("real_name"),rs.getString("phone"),rs.getString("role"),rs.getBoolean("enabled"),rs.getLong("auth_version"),rs.getLong("version"));}
 public Optional<User> user(long id,boolean lock){return jdbc.query("select "+USER_COLUMNS+" from public.sys_user where id=?"+(lock?" for update":""),(rs,n)->user(rs),id).stream().findFirst();}
 public Optional<User> byUsername(String name){return jdbc.query("select "+USER_COLUMNS+" from public.sys_user where username=?",(rs,n)->user(rs),name).stream().findFirst();}
 public Optional<Role> role(long id){return jdbc.query("select id,code,name,enabled,version from public.sys_role where id=?",(rs,n)->new Role(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)),id).stream().findFirst();}
 public Set<String> permissions(long user){return new TreeSet<>(jdbc.queryForList("select distinct p.code from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id and r.enabled join public.sys_role_permission rp on rp.role_id=r.id join public.sys_permission p on p.id=rp.permission_id and p.available where ur.user_id=?",String.class,user));}
 public Set<String> potentialPermissions(long user){return new TreeSet<>(jdbc.queryForList("select distinct p.code from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id join public.sys_role_permission rp on rp.role_id=r.id join public.sys_permission p on p.id=rp.permission_id and p.available where ur.user_id=?",String.class,user));}
 public Set<String> rolePermissions(long role){return new TreeSet<>(jdbc.queryForList("select p.code from public.sys_role_permission rp join public.sys_permission p on p.id=rp.permission_id and p.available where rp.role_id=?",String.class,role));}
 public List<Role> rolesFor(long user,boolean activeOnly){return jdbc.query("select r.id,r.code,r.name,r.enabled,r.version from public.sys_role r join public.sys_user_role ur on ur.role_id=r.id where ur.user_id=?"+(activeOnly?" and r.enabled":"")+" order by r.id",(rs,n)->new Role(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)),user);}
 public List<Long> affectedUsers(long role){return jdbc.queryForList("select user_id from public.sys_user_role where role_id=? order by user_id",Long.class,role);}
 public void lockGuard(){jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);}
 public void lockUsers(List<Long> ids){if(ids.isEmpty())return;String csv=String.join(",",new TreeSet<>(ids).stream().map(String::valueOf).toList());jdbc.queryForList("select id from public.sys_user where id=any(string_to_array(?,',')::bigint[]) order by id for update",csv);}
 public Map<Long,Set<String>> potentialPermissionsForUsers(List<Long> ids){Map<Long,Set<String>> result=new HashMap<>();if(ids.isEmpty())return result;String csv=String.join(",",ids.stream().map(String::valueOf).toList());jdbc.query("select distinct ur.user_id,p.code from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id join public.sys_role_permission rp on rp.role_id=r.id join public.sys_permission p on p.id=rp.permission_id and p.available where ur.user_id=any(string_to_array(?,',')::bigint[])",rs->{while(rs.next())result.computeIfAbsent(rs.getLong(1),k->new TreeSet<>()).add(rs.getString(2));return null;},csv);return result;}

 public long managers(){return jdbc.queryForObject("select count(*) from public.sys_user u where u.enabled and not exists(select 1 from public.sys_permission p where p.available and not exists(select 1 from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id and r.enabled join public.sys_role_permission rp on rp.role_id=r.id where ur.user_id=u.id and rp.permission_id=p.id))",Long.class);}

 public long createUser(String name,String hash,String real,String phone,String legacy){return jdbc.queryForObject("insert into public.sys_user(username,password_hash,real_name,phone,role) values (?,?,?,?,?) returning id",Long.class,name,hash,real,phone,legacy);}
 public int basics(long id,long version,String name,String phone){return jdbc.update("update public.sys_user set real_name=?,phone=?,version=version+1 where id=? and version=?",name,phone,id,version);}
 public int enabled(long id,long version,boolean enabled){return jdbc.update("update public.sys_user set enabled=?,auth_version=auth_version+1,version=version+1 where id=? and version=?",enabled,id,version);}
 public int password(long id,long version,String hash){return jdbc.update("update public.sys_user set password_hash=?,auth_version=auth_version+1,version=version+1 where id=? and version=?",hash,id,version);}
 public int revoke(long id,long version){return jdbc.update("update public.sys_user set auth_version=auth_version+1,version=version+1 where id=? and version=?",id,version);}
 public void replaceRoles(long user,List<Long> roles){jdbc.update("delete from public.sys_user_role where user_id=?",user);for(Long role:new TreeSet<>(roles))jdbc.update("insert into public.sys_user_role(user_id,role_id) values (?,?)",user,role);}
 public long createRole(String code,String name){return jdbc.queryForObject("insert into public.sys_role(code,name) values (?,?) returning id",Long.class,code,name);}
 public int roleBasics(long id,long version,String name,boolean enabled){return jdbc.update("update public.sys_role set name=?,enabled=?,version=version+1 where id=? and version=?",name,enabled,id,version);}
 public void replacePermissions(long role,Set<String> codes){jdbc.update("delete from public.sys_role_permission where role_id=?",role);for(String code:codes)jdbc.update("insert into public.sys_role_permission(role_id,permission_id) select ?,id from public.sys_permission where code=? and available",role,code);}
 public void revokeRoleUsers(long role){jdbc.update("update public.sys_user set auth_version=auth_version+1,version=version+1 where id in (select user_id from public.sys_user_role where role_id=?)",role);}
 public Set<String> catalog(){return new TreeSet<>(jdbc.queryForList("select code from public.sys_permission where available",String.class));}
 private static final String USER_SCOPE="not exists(select 1 from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id join public.sys_role_permission rp on rp.role_id=r.id join public.sys_permission p on p.id=rp.permission_id and p.available where ur.user_id=u.id and not(p.code=any(string_to_array(?,','))))";
 private static final String ROLE_SCOPE="not exists(select 1 from public.sys_role_permission rp join public.sys_permission p on p.id=rp.permission_id and p.available where rp.role_id=r.id and not(p.code=any(string_to_array(?,','))))";
 public long userCount(String csv,String keyword){return jdbc.queryForObject("select count(*) from public.sys_user u where "+USER_SCOPE+" and (u.username like ? or u.real_name like ?)",Long.class,csv,"%"+keyword+"%","%"+keyword+"%");}
 public List<User> users(String csv,String keyword,int size,long offset){return jdbc.query("select u.* from public.sys_user u where "+USER_SCOPE+" and (u.username like ? or u.real_name like ?) order by u.id limit ? offset ?",(rs,n)->user(rs),csv,"%"+keyword+"%","%"+keyword+"%",size,offset);}
 public long roleCount(String csv){return jdbc.queryForObject("select count(*) from public.sys_role r where "+ROLE_SCOPE,Long.class,csv);}
 public List<Role> roles(String csv,int size,long offset){return jdbc.query("select r.id,r.code,r.name,r.enabled,r.version from public.sys_role r where "+ROLE_SCOPE+" order by r.id limit ? offset ?",(rs,n)->new Role(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5)),csv,size,offset);}
 public Map<Long,List<Map<String,Object>>> roleViewsForUsers(List<Long> ids){
  Map<Long,List<Map<String,Object>>> result=new HashMap<>();if(ids.isEmpty())return result;
  String csv=String.join(",",ids.stream().map(String::valueOf).toList());
  jdbc.query("select ur.user_id,r.id,r.code,r.name,r.enabled,r.version from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id where ur.user_id=any(string_to_array(?,',')::bigint[]) order by r.id",rs->{while(rs.next()){var v=new LinkedHashMap<String,Object>();v.put("id",Long.toString(rs.getLong("id")));v.put("code",rs.getString("code"));v.put("name",rs.getString("name"));v.put("enabled",rs.getBoolean("enabled"));v.put("version",rs.getLong("version"));result.computeIfAbsent(rs.getLong("user_id"),k->new ArrayList<>()).add(v);}return null;},csv);return result;
 }
 public Map<Long,Set<String>> rolePermissionsFor(List<Long> ids){Map<Long,Set<String>> result=new HashMap<>();if(ids.isEmpty())return result;String csv=String.join(",",ids.stream().map(String::valueOf).toList());jdbc.query("select rp.role_id,p.code from public.sys_role_permission rp join public.sys_permission p on p.id=rp.permission_id and p.available where rp.role_id=any(string_to_array(?,',')::bigint[])",rs->{while(rs.next())result.computeIfAbsent(rs.getLong(1),k->new TreeSet<>()).add(rs.getString(2));return null;},csv);return result;}
 public List<Map<String,Object>> catalogPage(int size,long offset){return jdbc.query("select id,code,description,module,available from public.sys_permission order by id limit ? offset ?",(rs,n)->Map.<String,Object>of("id",Long.toString(rs.getLong(1)),"code",rs.getString(2),"description",rs.getString(3),"module",rs.getString(4),"available",rs.getBoolean(5)),size,offset);}
 public long catalogCount(){return jdbc.queryForObject("select count(*) from public.sys_permission",Long.class);}
}
