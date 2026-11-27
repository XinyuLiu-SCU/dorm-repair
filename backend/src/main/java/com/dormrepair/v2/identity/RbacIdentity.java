package com.dormrepair.v2.identity;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class RbacIdentity {
 private final RbacRepository repo;
 public RbacIdentity(RbacRepository repo){this.repo=repo;}
 public record Identity(RbacRepository.User user,Set<String> permissions){}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Identity authenticate(Jwt jwt){
  long id;try{id=Long.parseLong(jwt.getSubject());if(id<=0)throw new NumberFormatException();}catch(Exception e){throw unauthorized();}
  var user=repo.user(id,false).orElseThrow(RbacIdentity::unauthorized);
  Object version=jwt.getClaim("authVersion");
  if(!user.enabled() || !(version instanceof Number) || !Long.toString(user.authVersion()).equals(version.toString()))throw unauthorized();
  return new Identity(user,repo.permissions(id));
 }
 public Jwt jwt(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof Jwt jwt))throw unauthorized();return jwt;}
 public Identity actor(){return authenticate(jwt());}
 public Identity require(String permission){var actor=actor();if(!actor.permissions().contains(permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Operation not permitted");return actor;}
 public static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication rejected");}
 public static String legacyScope(Identity identity){String role=identity.user().legacyRole();return identity.permissions().contains("legacy:"+role.toLowerCase(Locale.ROOT))?role:"";}
}
