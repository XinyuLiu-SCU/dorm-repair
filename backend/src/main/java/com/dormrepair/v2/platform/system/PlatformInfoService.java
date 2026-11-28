package com.dormrepair.v2.platform.system;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
@Service
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PlatformInfoService {
 private final Flyway flyway; private final boolean core;
 public PlatformInfoService(Flyway flyway,@org.springframework.beans.factory.annotation.Value("${app.v2.core.enabled:false}") boolean core){this.flyway=flyway;this.core=core;}
 public Info info(){var current=flyway.info().current();return new Info("v2",core?"V2.0 Final — Core":"V2.0 Final — E",current==null?null:current.getVersion().getVersion(),core);}
 public record Info(String apiVersion,String stage,String businessMigrationVersion,boolean workflowEnabled){}
}
