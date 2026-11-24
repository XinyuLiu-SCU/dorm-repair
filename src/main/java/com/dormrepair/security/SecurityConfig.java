package com.dormrepair.security;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration
public class SecurityConfig {
 @Bean SecretKeySpec jwtKey(@Value("${app.jwt.secret}") String secret) {
  if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET requires at least 32 bytes");
  return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
 }
 @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
 @Bean JwtDecoder jwtDecoder(SecretKeySpec key) {
  var d=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  d.setJwtValidator(JwtValidators.createDefaultWithIssuer("dorm-repair")); return d;
 }
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean org.springframework.security.core.userdetails.UserDetailsService noDefaultUsers() {
  return name -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException(name); };
 }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  http.csrf(c->c.disable()).cors(c->{}).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
    .requestMatchers("/api/auth/me").authenticated()
    .requestMatchers(HttpMethod.GET,"/api/users/repairers").hasAuthority("SCOPE_ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/repair-orders/*", "/api/repair-orders/*/records", "/api/repair-orders/*/workflow", "/api/workflow/tasks").hasAnyAuthority("SCOPE_STUDENT","SCOPE_REPAIRER","SCOPE_ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/repair-orders").hasAnyAuthority("SCOPE_STUDENT","SCOPE_REPAIRER","SCOPE_ADMIN")
    .requestMatchers(HttpMethod.POST,"/api/repair-orders").hasAuthority("SCOPE_STUDENT")
    .requestMatchers("/api/repair-orders/*/approve","/api/repair-orders/*/reject","/api/repair-orders/*/assign","/api/db-test").hasAuthority("SCOPE_ADMIN")
    .requestMatchers("/api/repair-orders/*/start","/api/repair-orders/*/finish").hasAuthority("SCOPE_REPAIRER")
    .requestMatchers("/api/repair-orders/*/accept").hasAuthority("SCOPE_STUDENT").anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((q,r,e)->error(r,401)))
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->error(r,401)).accessDeniedHandler((q,r,x)->error(r,403)));
  return http.build();
 }
 private static void error(jakarta.servlet.http.HttpServletResponse r,int code) throws java.io.IOException {
  r.setStatus(code); r.setContentType("application/json;charset=UTF-8"); r.getWriter().write("{\"code\":"+code+",\"message\":\""+(code==401?"Unauthorized":"Forbidden")+"\"}");
 }
 @Bean CorsConfigurationSource cors(@Value("${app.cors.origins}") List<String> origins) {
  var c=new CorsConfiguration(); c.setAllowedOrigins(origins); c.setAllowedMethods(List.of("GET","POST","OPTIONS")); c.setAllowedHeaders(List.of("Authorization","Content-Type"));
  var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**",c); return source;
 }
}
