package com.dormrepair.workflow;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
/** Proves ordinary startup does not initialize Flowable or connect for engine DDL. */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties={"app.jwt.secret=disabled-engine-test-secret-at-least-32-bytes", "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/must_not_connect"})
class DefaultWorkflowDisabledTest {
 @Autowired ApplicationContext context;
 @Test void writeGateIsClosed() {
  com.dormrepair.service.RepairWorkflowService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(com.dormrepair.service.RepairWorkflowService.class));
  var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,target::requireEnabled);
  assertEquals(503,ex.getStatusCode().value());
 }
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
 @Test void databaseConnectionFailureReturns503() throws Exception {
  var now=java.time.Instant.now();var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("dorm-repair").subject("1").issuedAt(now).expiresAt(now.plusSeconds(60)).claim("scope","STUDENT").build();
  String token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/repair-orders/1").header("Authorization","Bearer "+token))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(503));
 }
 @Test void processEngineDisabledByDefault() {
  assertTrue(context.getBeansOfType(ProcessEngine.class).isEmpty());
 }
}
