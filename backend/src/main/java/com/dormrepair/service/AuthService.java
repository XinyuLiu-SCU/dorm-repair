package com.dormrepair.service;
import com.dormrepair.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AuthService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private com.dormrepair.v2.identity.RbacService versionedIdentity;
 private final UserRepository users; private final PasswordEncoder passwords; private final JwtEncoder encoder; private final long ttl;
 public AuthService(UserRepository users,PasswordEncoder passwords,JwtEncoder encoder,@Value("${app.jwt.ttl-seconds:3600}") long ttl) {
  if(ttl<1||ttl>86400) throw new IllegalArgumentException("Invalid JWT TTL");
  this.users=users; this.passwords=passwords; this.encoder=encoder; this.ttl=ttl;
 }
 public record LoginResult(String accessToken,String tokenType,long expiresIn) {}
 public LoginResult login(String username,String password) {
  if(versionedIdentity!=null) return versionedIdentity.login(username,password);
  if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw invalid();
  var u=users.find(username).orElseThrow(AuthService::invalid);
  if(!Set.of("STUDENT","REPAIRER","ADMIN").contains(u.role())||u.passwordHash()==null||!u.passwordHash().startsWith("$2")||!passwords.matches(password,u.passwordHash())) throw invalid();
  var now=Instant.now(); var claims=JwtClaimsSet.builder().issuer("dorm-repair").subject(u.id().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl)).claim("scope",u.role()).claim("username",u.username()).build();
  return new LoginResult(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),"Bearer",ttl);
 }
 private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password"); }
}
