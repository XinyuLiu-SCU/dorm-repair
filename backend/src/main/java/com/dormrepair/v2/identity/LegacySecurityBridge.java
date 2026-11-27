package com.dormrepair.v2.identity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class LegacySecurityBridge {
 @Bean @Order(2) SecurityFilterChain versionAwareV1(HttpSecurity http,RbacJwtConverter converter)throws Exception{
  http.securityMatcher("/api/**").csrf(c->c.disable()).cors(c->{}).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
    .requestMatchers("/api/auth/me").hasAnyAuthority("SCOPE_STUDENT","SCOPE_REPAIRER","SCOPE_ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/users/repairers").hasAuthority("SCOPE_ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/repair-orders/*","/api/repair-orders/*/records","/api/repair-orders/*/workflow","/api/workflow/tasks").hasAnyAuthority("SCOPE_STUDENT","SCOPE_REPAIRER","SCOPE_ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/repair-orders").hasAnyAuthority("SCOPE_STUDENT","SCOPE_REPAIRER","SCOPE_ADMIN")
    .requestMatchers(HttpMethod.POST,"/api/repair-orders").hasAuthority("SCOPE_STUDENT")
    .requestMatchers("/api/repair-orders/*/approve","/api/repair-orders/*/reject","/api/repair-orders/*/assign","/api/db-test").hasAuthority("SCOPE_ADMIN")
    .requestMatchers("/api/repair-orders/*/start","/api/repair-orders/*/finish").hasAuthority("SCOPE_REPAIRER")
    .requestMatchers("/api/repair-orders/*/accept").hasAuthority("SCOPE_STUDENT").anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)).authenticationEntryPoint((q,r,e)->error(r,unavailable(e)?503:401)))
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->error(r,unavailable(x)?503:401)).accessDeniedHandler((q,r,x)->error(r,403)));
  return http.build();
 }
 public static boolean unavailable(Exception ex){return ex instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException auth&&"temporarily_unavailable".equals(auth.getError().getErrorCode());}
 private static void error(jakarta.servlet.http.HttpServletResponse r,int status)throws java.io.IOException{r.setStatus(status);r.setContentType("application/json;charset=UTF-8");r.getWriter().write("{\"code\":"+status+",\"message\":\""+(status==401?"Unauthorized":status==503?"Service unavailable":"Forbidden")+"\"}");}
}
