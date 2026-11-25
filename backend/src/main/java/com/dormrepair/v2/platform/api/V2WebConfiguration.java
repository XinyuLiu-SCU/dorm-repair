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
 @Bean @Order(1) SecurityFilterChain v2Security(HttpSecurity http,ObjectMapper json,@Value("${app.cors.origins}") List<String> origins)throws Exception {
  var cors=new CorsConfiguration();cors.setAllowedOrigins(origins);cors.setAllowedMethods(List.of("GET","OPTIONS"));cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-Id"));cors.setExposedHeaders(List.of("X-Request-Id"));
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/v2/**",cors);
  http.securityMatcher("/api/v2/**").csrf(c->c.disable()).cors(c->c.configurationSource(source)).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/v2/system/info").authenticated().anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((q,r,e)->write(json,q,r,401,"UNAUTHORIZED","Authentication required")))
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->write(json,q,r,401,"UNAUTHORIZED","Authentication required")).accessDeniedHandler((q,r,x)->write(json,q,r,403,"FORBIDDEN","Operation is not permitted")));
  return http.build();
 }
 private static void write(ObjectMapper json,jakarta.servlet.http.HttpServletRequest q,jakarta.servlet.http.HttpServletResponse r,int status,String code,String message)throws java.io.IOException {
  r.setStatus(status);r.setContentType("application/json;charset=UTF-8");json.writeValue(r.getOutputStream(),new V2Error(status,code,message,V2RequestTraceFilter.requestId(q),List.of()));
 }
}
