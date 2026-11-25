package com.dormrepair.workflow;
import java.net.*;
import java.net.http.*;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="flowable.stageD",matches="true")
@DirtiesContext
class IntegrationProfileSmokeTest {
 @DynamicPropertySource static void secret(DynamicPropertyRegistry registry) {
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
  String value=java.util.Base64.getEncoder().encodeToString(key);
  registry.add("INTEGRATION_JWT_SECRET",()->value);
 }
 @LocalServerPort int port;
 @Autowired DataSource dataSource;
 @Autowired ProcessEngine engine;
 @Test void frontendProfileUsesIsolatedDatabaseAndStartsRealHttpWithoutDdl() throws Exception {
  try(var c=dataSource.getConnection()){assertEquals("dorm_repair_flowable_a_test_20261123",c.getCatalog());}
  assertEquals("false",engine.getProcessEngineConfiguration().getDatabaseSchemaUpdate());
  var r=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(401,r.statusCode());
 }
}
