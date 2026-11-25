package com.dormrepair.v2.platform.system;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
@Service
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PlatformInfoService {
 private final Flyway flyway;
 public PlatformInfoService(Flyway flyway){this.flyway=flyway;}
 public Info info(){var current=flyway.info().current();return new Info("v2","V2.0-A",current==null?null:current.getVersion().getVersion(),false);}
 public record Info(String apiVersion,String stage,String businessMigrationVersion,boolean workflowEnabled){}
}
