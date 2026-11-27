package com.dormrepair.v2.identity;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.*;
import com.dormrepair.v2.platform.api.V2RequestTraceFilter;
@Repository
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class SecurityAudit {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public SecurityAudit(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public void record(Long actor,String action,String type,Long target,Map<String,Object> details){
  var attrs=RequestContextHolder.getRequestAttributes();String requestId=attrs instanceof ServletRequestAttributes s?V2RequestTraceFilter.requestId(s.getRequest()):UUID.randomUUID().toString();
  try{jdbc.update("insert into public.audit_event(actor_id,action,target_type,target_id,request_id,details) values (?,?,?,?,?,?::jsonb)",actor,action,type,target,requestId,json.writeValueAsString(details));}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Cannot encode safe audit metadata");}
 }
 public long count(){return jdbc.queryForObject("select count(*) from public.audit_event",Long.class);}
 public List<Map<String,Object>> page(int size,long offset){return jdbc.query("select id,actor_id,action,target_type,target_id,request_id,result,details::text,created_at from public.audit_event order by created_at desc,id desc limit ? offset ?",(rs,n)->{
  var m=new LinkedHashMap<String,Object>();m.put("id",Long.toString(rs.getLong(1)));m.put("actorId",rs.getObject(2)==null?null:Long.toString(rs.getLong(2)));m.put("action",rs.getString(3));m.put("targetType",rs.getString(4));m.put("targetId",rs.getObject(5)==null?null:Long.toString(rs.getLong(5)));m.put("requestId",rs.getString(6));m.put("result",rs.getString(7));try{m.put("details",json.readTree(rs.getString(8)));}catch(Exception ex){throw new IllegalStateException("Invalid audit metadata");}m.put("createdAt",rs.getObject(9,java.time.OffsetDateTime.class).toString());return m;
 },size,offset);}
}
