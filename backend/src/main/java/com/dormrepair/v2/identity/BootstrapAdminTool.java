package com.dormrepair.v2.identity;
import java.util.Arrays;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** Credentials come only from V2_BOOTSTRAP_* environment or the local console, not command arguments. */
public final class BootstrapAdminTool {
 public static void main(String[] args){
  if(args.length!=0)throw new IllegalArgumentException("No credential command arguments allowed");
  String user=System.getenv("V2_DB_USER");if(user==null||user.isBlank())throw new IllegalStateException("V2_DB_USER required");
  String username=System.getenv("V2_BOOTSTRAP_ADMIN_USERNAME");String password=System.getenv("V2_BOOTSTRAP_ADMIN_PASSWORD");
  var console=System.console();
  if(username==null&&console!=null)username=console.readLine("Synthetic administrator username: ");
  if(password==null&&console!=null){char[] chars=console.readPassword("Initial password (not echoed): ");if(chars!=null){password=new String(chars);Arrays.fill(chars,'\0');}}
  if(username==null||password==null)throw new IllegalStateException("Use a secure console or V2_BOOTSTRAP_ADMIN_USERNAME/V2_BOOTSTRAP_ADMIN_PASSWORD");
  var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_dev",user,System.getenv().getOrDefault("V2_DB_PASSWORD",""));
  var result=BootstrapAdministrator.initialize(ds,new BCryptPasswordEncoder(),username,password);
  System.out.println(result.state()+" userId="+result.userId()+"; no credentials printed or overwritten");
 }
}
