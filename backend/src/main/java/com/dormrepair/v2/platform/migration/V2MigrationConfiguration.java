package com.dormrepair.v2.platform.migration;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class V2MigrationConfiguration {
    @Bean @ConditionalOnProperty(name="app.v2.enabled",havingValue="true") Object v2EnvironmentGuard(Environment env) {
        if(env.containsProperty("spring.flyway.url")||env.containsProperty("spring.flyway.user")||env.containsProperty("spring.flyway.password"))throw new IllegalStateException("V2 Flyway must share the application DataSource; separate Flyway credentials/URL are forbidden");
        String database=V2DatabaseTarget.database(env.getRequiredProperty("spring.datasource.url"));
        String mode=env.getRequiredProperty("app.v2.environment");
        if(!mode.equals("DEV")&&!mode.equals("TEST"))throw new IllegalStateException("V2 environment must be DEV or TEST");
        if(mode.equals("DEV")&&!database.equals("dorm_repair_v2_dev") || mode.equals("TEST")&&!database.startsWith("dorm_repair_v2_test"))throw new IllegalStateException("V2 profile/database mismatch");
        int port=env.getProperty("server.port",Integer.class,18081);
        if((port!=18081&&port!=0)||!"127.0.0.1".equals(env.getProperty("server.address")))throw new IllegalStateException("V2 binds 127.0.0.1:18081 only (random port allowed for tests)");
        for(String key:new String[]{"flowable.process.enabled","flowable.database-schema-update","flowable.async-executor-activate","app.workflow.enabled"})if(!"false".equals(env.getProperty(key)))throw new IllegalStateException("V2.0-A must keep workflow and automatic engine DDL disabled");
        if(!env.getProperty("spring.flyway.enabled",Boolean.class,false))throw new IllegalStateException("V2 foundation requires guarded migrations");
        return new Object();
    }
    @Bean @ConditionalOnProperty(name="app.v2.enabled",havingValue="true") @DependsOn("v2EnvironmentGuard")
    FlywayMigrationStrategy guardedV2Migration(DataSource ds) {
        return flyway -> {
            verifyConfiguration(flyway);
            if(flyway.getConfiguration().getDataSource()!=ds)throw new IllegalStateException("V2 Flyway must use the guarded application DataSource");
            try(var c=ds.getConnection()){LegacySchemaVerifier.verifyBeforeFirstMigration(c);}
            catch(java.sql.SQLException e){throw new IllegalStateException("Cannot verify isolated V2 schema",e);}
            flyway.migrate();
        };
    }
    @Bean @ConditionalOnProperty(name="app.v2.enabled",havingValue="false",matchIfMissing=true)
    FlywayMigrationStrategy rejectUnscopedFlyway(){return flyway->{throw new IllegalStateException("Flyway is blocked outside the explicitly guarded V2 environment");};}
    public static void verifyConfiguration(Flyway flyway) {
        var c=flyway.getConfiguration();
        if(c.isBaselineOnMigrate()||!c.isCleanDisabled()||!c.isValidateOnMigrate()||!c.isGroup()||c.isMixed())throw new IllegalStateException("Unsafe V2 Flyway options");
        if(!"v2_business_history".equals(c.getDefaultSchema()) || !java.util.Arrays.equals(c.getSchemas(),new String[]{"v2_business_history"}))throw new IllegalStateException("Flyway manages only its separate business history schema");
    }
}
