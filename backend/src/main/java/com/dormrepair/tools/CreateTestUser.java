package com.dormrepair.tools;
import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** Explicit standalone tool: inserts a new username only; never updates existing users. */
public class CreateTestUser {
 public static void main(String[] args) throws Exception {
  if(args.length!=2 || !Set.of("STUDENT","REPAIRER","ADMIN").contains(args[1])) throw new IllegalArgumentException("Usage: CreateTestUser username ROLE");
  var console=System.console();
  if(console==null) throw new IllegalStateException("Run in an interactive terminal");
  char[] password=console.readPassword("New account password (12+ characters): ");
  if(password==null) throw new IllegalArgumentException("Password required");
  try {
   if(password.length<12 || new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must be 12+ characters and at most 72 UTF-8 bytes");
   String hash=new BCryptPasswordEncoder().encode(new String(password));
   String dbPassword=System.getenv().getOrDefault("DB_PASSWORD", "");
   try(var c=DriverManager.getConnection(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/dorm_repair_db"),System.getenv().getOrDefault("DB_USER","sota"),dbPassword);
       var st=c.prepareStatement("insert into sys_user (username, password_hash, real_name, role) values (?, ?, ?, ?) on conflict (username) do nothing")) {
    st.setString(1,args[0]); st.setString(2,hash); st.setString(3,args[0]); st.setString(4,args[1]);
    System.out.println(st.executeUpdate()==1?"Created account":"Username already exists; unchanged");
   }
  } finally { Arrays.fill(password,' '); }
 }
}
