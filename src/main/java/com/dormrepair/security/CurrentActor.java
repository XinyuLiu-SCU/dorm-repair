package com.dormrepair.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public record CurrentActor(Long id, String role) {
    public static CurrentActor require(String role) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (!role.equals(jwt.getClaimAsString("scope")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role not permitted");
        try { return new CurrentActor(Long.valueOf(jwt.getSubject()), role); }
        catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid identity"); }
    }
    public static CurrentActor current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        String role = jwt.getClaimAsString("scope");
        if (role == null || !java.util.Set.of("STUDENT", "REPAIRER", "ADMIN").contains(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role not permitted");
        return require(role);
    }

    public void owns(Long owner) {
        if (!id.equals(owner)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order does not belong to current user");
    }
}
