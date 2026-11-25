package com.dormrepair.v2.platform.migration;

import java.sql.*;
import java.util.*;

/** Read-only verified adoption of the known V1 business shape; never a Flyway baseline. */
public final class LegacySchemaVerifier {
    private record Column(String type, Integer length, boolean nullable) {}
    private static Column col(String type, Integer length, boolean nullable) { return new Column(type,length,nullable); }
    private static final Map<String, Map<String,Column>> CORE = Map.of(
        "sys_user", Map.of("id",col("bigint",null,false),"username",col("character varying",50,false),"password_hash",col("character varying",255,false),"real_name",col("character varying",50,false),"role",col("character varying",20,false),"phone",col("character varying",20,true),"created_at",col("timestamp without time zone",null,false)),
        "repair_order", Map.of("id",col("bigint",null,false),"student_id",col("bigint",null,false),"repairer_id",col("bigint",null,true),"title",col("character varying",100,false),"description",col("text",null,false),"location",col("character varying",200,false),"status",col("character varying",30,false),"created_at",col("timestamp without time zone",null,false),"updated_at",col("timestamp without time zone",null,false)),
        "repair_record", Map.of("id",col("bigint",null,false),"order_id",col("bigint",null,false),"repairer_id",col("bigint",null,false),"description",col("text",null,false),"created_at",col("timestamp without time zone",null,false)));
    private LegacySchemaVerifier() {}
    public static void verifyBeforeFirstMigration(Connection c) throws SQLException {
        V2DatabaseTarget.verify(c);
        if (hasAppliedMigration(c)) return; // Flyway validate owns subsequent version/checksum verification.
        Set<String> tables = new HashSet<>();
        try (var s=c.prepareStatement("select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE'");var rs=s.executeQuery()) { while(rs.next()) tables.add(rs.getString(1)); }
        long coreCount=CORE.keySet().stream().filter(tables::contains).count();
        if(coreCount==0 && tables.isEmpty()) return;
        if(coreCount!=3) fail("Expected an empty public schema or all three known V1 business tables");
        for(String table:tables) if(!CORE.containsKey(table) && !table.equals("repair_order_legacy") && !table.equals("dormrepair_schema_history") && !table.equals("stage_a_tx_probe") && !table.startsWith("act_") && !table.startsWith("flw_")) fail("Unrecognized existing public table; manual review required");
        boolean linked=columnExists(c,"repair_order","process_instance_id");
        for(var entry:CORE.entrySet()) {
            var expected=new HashMap<>(entry.getValue());
            if(linked && entry.getKey().equals("repair_order")) expected.put("process_instance_id",col("character varying",64,true));
            verifyColumns(c,entry.getKey(),expected);
            requireConstraint(c,entry.getKey(),"p",List.of("id"),null,null);
            try(var st=c.prepareStatement("select is_identity,column_default from information_schema.columns where table_schema='public' and table_name=? and column_name='id'")) {
                st.setString(1,entry.getKey());try(var rs=st.executeQuery()){rs.next();if(!"YES".equals(rs.getString(1)) && (rs.getString(2)==null || !rs.getString(2).contains("nextval("))) fail("V1 id must retain an identity/sequence default");}
            }
        }
        requireConstraint(c,"sys_user","u",List.of("username"),null,null);
        requireConstraint(c,"repair_order","f",List.of("student_id"),"sys_user",List.of("id"));
        requireConstraint(c,"repair_order","f",List.of("repairer_id"),"sys_user",List.of("id"));
        requireConstraint(c,"repair_record","f",List.of("order_id"),"repair_order",List.of("id"));
        requireConstraint(c,"repair_record","f",List.of("repairer_id"),"sys_user",List.of("id"));
        requireEnum(c,"sys_user","role",Set.of("STUDENT","REPAIRER","ADMIN"));
        requireEnum(c,"repair_order","status",Set.of("PENDING","APPROVED","REJECTED","ASSIGNED","IN_PROGRESS","WAITING_ACCEPTANCE","REWORK","COMPLETED"));
        for(String table:CORE.keySet()) requireDefault(c,table,"created_at","time");
        requireDefault(c,"repair_order","updated_at","time");requireDefault(c,"repair_order","status","PENDING");
        if(linked!=tables.contains("repair_order_legacy")) fail("Incomplete workflow association; do not adopt a partial migration");
        if(linked) {
            verifyColumns(c,"repair_order_legacy",Map.of("order_id",col("bigint",null,false),"recorded_at",col("timestamp without time zone",null,false)));
            requireConstraint(c,"repair_order_legacy","p",List.of("order_id"),null,null);
            requireConstraint(c,"repair_order_legacy","f",List.of("order_id"),"repair_order",List.of("id"));
            try(var st=c.createStatement();var rs=st.executeQuery("select count(*) from public.repair_order o where process_instance_id is null and not exists(select 1 from public.repair_order_legacy l where l.order_id=o.id)")){rs.next();if(rs.getLong(1)!=0)fail("Unregistered unlinked order; manual review required");}
        }
    }
    private static boolean hasAppliedMigration(Connection c)throws SQLException {
        try(var s=c.createStatement();var rs=s.executeQuery("select to_regclass('v2_business_history.flyway_schema_history') is not null")){rs.next();if(!rs.getBoolean(1)) return false;}
        try(var s=c.createStatement();var rs=s.executeQuery("select count(*) from v2_business_history.flyway_schema_history where success and version is not null")){rs.next();return rs.getLong(1)>0;}
    }
    private static boolean columnExists(Connection c,String table,String column)throws SQLException {
        try(var s=c.prepareStatement("select count(*) from information_schema.columns where table_schema='public' and table_name=? and column_name=?")){s.setString(1,table);s.setString(2,column);try(var rs=s.executeQuery()){rs.next();return rs.getInt(1)>0;}}
    }
    private static void verifyColumns(Connection c,String table,Map<String,Column> expected)throws SQLException {
        Map<String,Column> actual=new HashMap<>();
        try(var s=c.prepareStatement("select column_name,data_type,character_maximum_length,is_nullable from information_schema.columns where table_schema='public' and table_name=?")){s.setString(1,table);try(var rs=s.executeQuery()){while(rs.next())actual.put(rs.getString(1),col(rs.getString(2),(Integer)rs.getObject(3),"YES".equals(rs.getString(4))));}}
        if(!actual.equals(expected))fail("Existing V1 column shape differs for "+table+"; no changes applied");
    }
    private static List<String> columns(Connection c,String table,java.sql.Array keys)throws SQLException {
        if(keys==null)return List.of();List<String> names=new ArrayList<>();
        for(Object key:(Object[])keys.getArray())try(var s=c.prepareStatement("select attname from pg_attribute where attrelid=('public.'||?)::regclass and attnum=?")){s.setString(1,table);s.setInt(2,((Number)key).intValue());try(var rs=s.executeQuery()){rs.next();names.add(rs.getString(1));}}
        return names;
    }
    private static void requireConstraint(Connection c,String table,String type,List<String> local,String foreign,List<String> remote)throws SQLException {
        try(var s=c.prepareStatement("select conkey,confkey,case when confrelid=0 then null else confrelid::regclass::text end,confdeltype from pg_constraint where conrelid=('public.'||?)::regclass and contype=?::\"char\"")) {
            s.setString(1,table);s.setString(2,type);try(var rs=s.executeQuery()){while(rs.next()){
                if(!columns(c,table,rs.getArray(1)).equals(local))continue;
                if(foreign==null)return;
                String ref=rs.getString(3);if(ref!=null && (ref.equals(foreign)||ref.equals("public."+foreign)) && columns(c,foreign,rs.getArray(2)).equals(remote) && Set.of("a","r").contains(rs.getString(4)))return;
            }}
        }fail("Required V1 constraint missing or unsafe for "+table);
    }
    private static void requireEnum(Connection c,String table,String column,Set<String> expected)throws SQLException {
        try(var s=c.prepareStatement("select conkey,pg_get_constraintdef(oid) from pg_constraint where conrelid=('public.'||?)::regclass and contype='c' and convalidated")){s.setString(1,table);try(var rs=s.executeQuery()){while(rs.next()){
            if(!columns(c,table,rs.getArray(1)).equals(List.of(column)))continue;
            var matcher=java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(rs.getString(2));Set<String> values=new HashSet<>();while(matcher.find())values.add(matcher.group(1));
            String normalized=rs.getString(2).replaceAll("::(?:character varying|text)(?:\\[\\])?","").replaceAll("[()\\s]","").toUpperCase(java.util.Locale.ROOT);
            if(values.equals(expected) && normalized.matches("CHECK"+column.toUpperCase(java.util.Locale.ROOT)+"=ANYARRAY\\['[A-Z_]+'(?:,'[A-Z_]+')*\\]"))return;
        }}}fail("V1 enum constraint differs for "+table);
    }
    private static void requireDefault(Connection c,String table,String column,String kind)throws SQLException {
        try(var s=c.prepareStatement("select column_default from information_schema.columns where table_schema='public' and table_name=? and column_name=?")){s.setString(1,table);s.setString(2,column);try(var rs=s.executeQuery()){rs.next();String value=rs.getString(1);if(value==null || (kind.equals("time") ? !(value.equalsIgnoreCase("CURRENT_TIMESTAMP")||value.equalsIgnoreCase("now()")) : !value.contains("'PENDING'")))fail("V1 default differs for "+table);}}
    }
    private static void fail(String reason) { throw new IllegalStateException(reason); }
}
