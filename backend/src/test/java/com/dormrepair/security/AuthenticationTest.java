package com.dormrepair.security;
import com.dormrepair.repository.UserRepository;
import com.dormrepair.service.AuthService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
class AuthenticationTest {
 SecurityConfig config=new SecurityConfig();
 UserRepository users=mock(UserRepository.class);
 JwtEncoder encoder; JwtDecoder decoder; AuthService auth;
 @BeforeEach void setup() {
  var key=config.jwtKey("unit-test-only-key-32-bytes-long-12345");
  encoder=config.jwtEncoder(key); decoder=config.jwtDecoder(key);
  auth=new AuthService(users,config.passwordEncoder(),encoder,3600);
 }
 @Test void loginAndDecodeAllRoles() {
  for(String role:new String[]{"STUDENT","REPAIRER","ADMIN"}) {
   when(users.find("demo")).thenReturn(Optional.of(new UserRepository.User(7L,"demo",config.passwordEncoder().encode("test-password"),"Demo",role)));
   var result=auth.login("demo","test-password"); var jwt=decoder.decode(result.accessToken());
   assertEquals("7",jwt.getSubject()); assertEquals(role,jwt.getClaimAsString("scope"));
   assertFalse(jwt.getClaims().containsKey("passwordHash")); assertEquals("Bearer",result.tokenType());
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.login("demo","wrong"));
  }
 }
 @Test void placeholderAndUnknownUserRejected() {
  when(users.find("demo")).thenReturn(Optional.of(new UserRepository.User(1L,"demo","placeholder","Demo","ADMIN")));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.login("demo","placeholder"));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->auth.login("missing","password"));
 }
 @Test void invalidTokensRejected() {
  var now=Instant.now();
  var claims=JwtClaimsSet.builder().issuer("dorm-repair").subject("7").issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).build();
  var expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
  assertThrows(JwtException.class,()->decoder.decode(expired));
  assertThrows(JwtException.class,()->decoder.decode("invalid"));
  var other=config.jwtDecoder(config.jwtKey("different-unit-test-key-32-bytes-12345"));
  when(users.find("demo")).thenReturn(Optional.of(new UserRepository.User(7L,"demo",config.passwordEncoder().encode("test-password"),"Demo","ADMIN")));
  assertThrows(JwtException.class,()->other.decode(auth.login("demo","test-password").accessToken()));
 }
 @Test void shortSecretRejected() { assertThrows(IllegalArgumentException.class,()->config.jwtKey("short")); }
}
