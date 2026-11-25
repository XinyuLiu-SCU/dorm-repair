package com.dormrepair.v2.platform.migration;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class V2SafetyTest {
 @Test void rejectsProductionOldIntegrationRemoteAndUrlParameters(){
  for(String url:new String[]{"jdbc:postgresql://localhost:5432/dorm_repair_db","jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123","jdbc:postgresql://remote:5432/dorm_repair_v2_dev","jdbc:postgresql://localhost:5432/dorm_repair_v2_test?currentSchema=public","jdbc:postgresql://localhost:5432/postgres"})assertThrows(IllegalStateException.class,()->V2DatabaseTarget.database(url));
 }
 @Test void acceptsOnlyExplicitLocalV2Names(){assertEquals("dorm_repair_v2_dev",V2DatabaseTarget.database("jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_dev"));assertEquals("dorm_repair_v2_test_legacy",V2DatabaseTarget.database("jdbc:postgresql://localhost:5432/dorm_repair_v2_test_legacy"));}
 @Test void resetRejectsDevelopmentEvenWithValidUrl()throws Exception {
  var c=mock(Connection.class);var md=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(md);when(md.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_dev");when(md.getDatabaseProductName()).thenReturn("PostgreSQL");when(c.getCatalog()).thenReturn("dorm_repair_v2_dev");assertThrows(IllegalStateException.class,()->V2DatabaseTarget.requireTestReset(c));verify(c,never()).createStatement();
 }
 @Test void catalogMismatchRejectedBeforeSql()throws Exception {
  var c=mock(Connection.class);var md=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(md);when(md.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_test");when(c.getCatalog()).thenReturn("dorm_repair_db");assertThrows(IllegalStateException.class,()->V2DatabaseTarget.verify(c));verify(c,never()).createStatement();
 }
 private MockEnvironment safe(){return new MockEnvironment().withProperty("app.v2.environment","DEV").withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:5432/dorm_repair_v2_dev").withProperty("server.port","18081").withProperty("server.address","127.0.0.1").withProperty("spring.flyway.enabled","true").withProperty("flowable.process.enabled","false").withProperty("flowable.database-schema-update","false").withProperty("flowable.async-executor-activate","false").withProperty("app.workflow.enabled","false");}
 @Test void profileRejectsProtectedPortsAndWorkflowActivation(){
  for(String port:new String[]{"8080","18080"})assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("server.port",port)));
  assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("flowable.process.enabled","true")));
  assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("flowable.async-executor-activate","true")));
 }
 @Test void unscopedFlywayCannotRunEvenIfManuallyEnabled(){var f=mock(org.flywaydb.core.Flyway.class);assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().rejectUnscopedFlyway().migrate(f));verify(f,never()).migrate();}
 @Test void separateFlywayTargetIsForbidden(){assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("spring.flyway.url","jdbc:postgresql://localhost:5432/dorm_repair_db")));}
 @Test void profileRejectsDevTestMismatchAndMissingMigration(){assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("app.v2.environment","TEST")));assertThrows(IllegalStateException.class,()->new V2MigrationConfiguration().v2EnvironmentGuard(safe().withProperty("spring.flyway.enabled","false")));}
}
