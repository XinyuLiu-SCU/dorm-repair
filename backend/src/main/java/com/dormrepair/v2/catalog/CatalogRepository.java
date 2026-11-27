package com.dormrepair.v2.catalog;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
@Repository @ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class CatalogRepository {
 public final JdbcTemplate jdbc; public CatalogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public JdbcTemplate jdbc(){return jdbc;}
 public void lock(){jdbc.queryForObject("select guard_key from public.system_guard where guard_key='rbac-admin' for update",String.class);jdbc.queryForObject("select guard_key from public.system_guard where guard_key='foundation-catalog' for update",String.class);}
 public Map<String,Object> row(String table,long id){var rows=jdbc.queryForList("select * from public."+table+" where id=?",id);return rows.isEmpty()?null:rows.getFirst();}
 public List<Map<String,Object>> rows(String table){return jdbc.queryForList("select * from public."+table+" order by sort_order,id");}
 public long insert(String table,Map<String,Object> values){String cols=String.join(",",values.keySet());String marks=String.join(",",Collections.nCopies(values.size(),"?"));return jdbc.queryForObject("insert into public."+table+"("+cols+") values("+marks+") returning id",Long.class,values.values().toArray());}
 public int update(String table,long id,int version,Map<String,Object> values){var args=new ArrayList<>(values.values());args.add(id);args.add(version);return jdbc.update("update public."+table+" set "+String.join(",",values.keySet().stream().map(k->k+"=?").toList())+",version=version+1 where id=? and version=?",args.toArray());}
}
