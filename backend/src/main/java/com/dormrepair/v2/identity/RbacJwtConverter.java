package com.dormrepair.v2.identity;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
@Component
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class RbacJwtConverter implements Converter<Jwt,AbstractAuthenticationToken> {
 private final RbacIdentity identity;
 public RbacJwtConverter(RbacIdentity identity){this.identity=identity;}
 @Override public AbstractAuthenticationToken convert(Jwt jwt){
  try{
   var actor=identity.authenticate(jwt);String legacy=RbacIdentity.legacyScope(actor);
   var authorities=new ArrayList<SimpleGrantedAuthority>();for(String code:actor.permissions())authorities.add(new SimpleGrantedAuthority("P_"+code));if(!legacy.isEmpty())authorities.add(new SimpleGrantedAuthority("SCOPE_"+legacy));
   var principal=Jwt.withTokenValue(jwt.getTokenValue()).headers(h->h.putAll(jwt.getHeaders())).claims(c->c.putAll(jwt.getClaims())).claim("scope",legacy).claim("username",actor.user().username()).build();
   return new JwtAuthenticationToken(principal,authorities,actor.user().username());
  }catch(org.springframework.dao.DataAccessException | org.springframework.transaction.CannotCreateTransactionException ex){throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"),"Identity service unavailable");}
  catch(org.springframework.web.server.ResponseStatusException ex){throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Authentication rejected");}
 }
}
