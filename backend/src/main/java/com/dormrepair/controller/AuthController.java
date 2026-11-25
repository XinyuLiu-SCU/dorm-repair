package com.dormrepair.controller;
import com.dormrepair.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth;
 public AuthController(AuthService auth) { this.auth=auth; }
 public record LoginRequest(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=72) String password) {}
 @PostMapping("/login") public AuthService.LoginResult login(@Valid @RequestBody LoginRequest r) { return auth.login(r.username(),r.password()); }
 @GetMapping("/me") public Map<String,Object> me(@AuthenticationPrincipal Jwt jwt) { return Map.of("id",Long.valueOf(jwt.getSubject()),"username",jwt.getClaimAsString("username"),"role",jwt.getClaimAsString("scope")); }
}
