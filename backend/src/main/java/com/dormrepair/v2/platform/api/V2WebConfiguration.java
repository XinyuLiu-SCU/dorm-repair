package com.dormrepair.v2.platform.api;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class V2WebConfiguration {
 @Bean FilterRegistrationBean<V2RequestTraceFilter> v2Tracing(){var bean=new FilterRegistrationBean<>(new V2RequestTraceFilter());bean.setOrder(Ordered.HIGHEST_PRECEDENCE+10);return bean;}
 @Bean @Order(1) SecurityFilterChain v2Security(HttpSecurity http,ObjectMapper json,@Value("${app.cors.origins}") List<String> origins,com.dormrepair.v2.identity.RbacJwtConverter converter)throws Exception {
  var cors=new CorsConfiguration();cors.setAllowedOrigins(origins);cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","OPTIONS"));cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-Id"));cors.setExposedHeaders(List.of("X-Request-Id"));
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/v2/**",cors);
  http.securityMatcher("/api/v2/**").csrf(c->c.disable()).cors(c->c.configurationSource(source)).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/v2/auth/login").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/v2/system/info","/api/v2/auth/me").authenticated()
    .requestMatchers(HttpMethod.GET,"/api/v2/profile").hasAuthority("P_profile:read")
    .requestMatchers(HttpMethod.PATCH,"/api/v2/profile").hasAuthority("P_profile:write")
    .requestMatchers(HttpMethod.POST,"/api/v2/profile/password").hasAuthority("P_profile:password")
    .requestMatchers(HttpMethod.GET,"/api/v2/users","/api/v2/users/*").hasAuthority("P_user:manage")
    .requestMatchers(HttpMethod.POST,"/api/v2/users","/api/v2/users/*/enable","/api/v2/users/*/disable","/api/v2/users/*/reset-password").hasAuthority("P_user:manage")
    .requestMatchers(HttpMethod.PATCH,"/api/v2/users/*").hasAuthority("P_user:manage")
    .requestMatchers(HttpMethod.PUT,"/api/v2/users/*/roles").hasAuthority("P_user:manage")
    .requestMatchers(HttpMethod.GET,"/api/v2/roles","/api/v2/roles/*").hasAuthority("P_role:manage")
    .requestMatchers(HttpMethod.POST,"/api/v2/roles","/api/v2/roles/*/enable","/api/v2/roles/*/disable").hasAuthority("P_role:manage")
    .requestMatchers(HttpMethod.PATCH,"/api/v2/roles/*").hasAuthority("P_role:manage")
    .requestMatchers(HttpMethod.PUT,"/api/v2/roles/*/permissions").hasAuthority("P_role:manage")
    .requestMatchers(HttpMethod.GET,"/api/v2/permissions").hasAuthority("P_permission:read")
    .requestMatchers(HttpMethod.GET,"/api/v2/audit-events").hasAuthority("P_audit:read")
    .requestMatchers("/api/v2/addresses","/api/v2/addresses/**","/api/v2/residencies","/api/v2/residencies/**","/api/v2/drafts","/api/v2/drafts/**","/api/v2/catalog/**","/api/v2/facilities","/api/v2/facilities/**").authenticated()
    .anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)).authenticationEntryPoint((q,r,e)->write(json,q,r,com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(e)?503:401,com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(e)?"SERVICE_UNAVAILABLE":"UNAUTHORIZED",com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(e)?"Identity service temporarily unavailable":"Authentication required")))
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->write(json,q,r,com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(x)?503:401,com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(x)?"SERVICE_UNAVAILABLE":"UNAUTHORIZED",com.dormrepair.v2.identity.LegacySecurityBridge.unavailable(x)?"Identity service temporarily unavailable":"Authentication required")).accessDeniedHandler((q,r,x)->write(json,q,r,403,"FORBIDDEN","Operation is not permitted")));
  return http.build();
 }
 private static void write(ObjectMapper json,jakarta.servlet.http.HttpServletRequest q,jakarta.servlet.http.HttpServletResponse r,int status,String code,String message)throws java.io.IOException {
  r.setStatus(status);r.setContentType("application/json;charset=UTF-8");json.writeValue(r.getOutputStream(),new V2Error(status,code,message,V2RequestTraceFilter.requestId(q),List.of()));
 }
}
