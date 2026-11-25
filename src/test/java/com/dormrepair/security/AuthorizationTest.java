package com.dormrepair.security;
import com.dormrepair.repository.*;
import com.dormrepair.service.*;
import com.dormrepair.controller.*;
import com.dormrepair.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(controllers={AuthController.class,RepairOrderController.class},properties={"app.jwt.secret=unit-test-only-key-32-bytes-long-12345","app.cors.origins=http://localhost:5173"})
@Import({SecurityConfig.class,AuthService.class,GlobalExceptionHandler.class})
class AuthorizationTest {
 @Autowired MockMvc mvc;
 @Autowired JwtEncoder encoder;
 @MockitoBean UserRepository users;
 @MockitoBean RepairOrderService orders;
 String token(String role) {
  var n=Instant.now();
  return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("dorm-repair").subject("7").issuedAt(n).expiresAt(n.plusSeconds(3600)).claim("scope",role).claim("username","demo").build())).getTokenValue();
 }
 @Test void anonymousAndInvalidToken() throws Exception {
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
 }
 @Test void meAndRoleRestrictions() throws Exception {
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
  mvc.perform(post("/api/repair-orders/1/approve").header("Authorization","Bearer "+token("STUDENT"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  mvc.perform(post("/api/repair-orders/1/approve").header("Authorization","Bearer "+token("ADMIN"))).andExpect(status().isOk());
  verify(orders).approveRepairOrder(1L);
  mvc.perform(post("/api/repair-orders/1/accept").header("Authorization","Bearer "+token("REPAIRER")).contentType("application/json").content("{\"approved\":true}")).andExpect(status().isForbidden());
 }
 @Test void spoofedActorIgnored() throws Exception {
  mvc.perform(post("/api/repair-orders").header("Authorization","Bearer "+token("STUDENT")).contentType("application/json").content("{\"studentId\":999,\"title\":\"Leak\",\"description\":\"Water\",\"location\":\"101\"}")).andExpect(status().isOk());
  verify(orders).createRepairOrder(7L,"Leak","Water","101");
  mvc.perform(post("/api/repair-orders/1/finish").header("Authorization","Bearer "+token("REPAIRER")).contentType("application/json").content("{\"repairerId\":999,\"description\":\"Fixed\"}")).andExpect(status().isOk());
  verify(orders).finishRepair(1L,7L,"Fixed");
  mvc.perform(post("/api/repair-orders/1/accept").header("Authorization","Bearer "+token("STUDENT")).contentType("application/json").content("{\"studentId\":999,\"approved\":true}")).andExpect(status().isOk());
  verify(orders).acceptRepair(1L,7L,true);
 }
 @Test void listIsScoped() throws Exception {
  for(String role:new String[]{"STUDENT","REPAIRER","ADMIN"}) {
   mvc.perform(get("/api/repair-orders").header("Authorization","Bearer "+token(role))).andExpect(status().isOk());
   verify(orders).getVisibleRepairOrders(7L,role);
  }
 }
 @Test void loginErrorsAndValidation() throws Exception {
  mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"missing\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
 @Test void browserCorsPreflight() throws Exception {
  mvc.perform(options("/api/auth/login").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
   .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
  mvc.perform(options("/api/auth/login").header("Origin","https://unapproved.example").header("Access-Control-Request-Method","POST"))
   .andExpect(status().isForbidden());
 }
}
