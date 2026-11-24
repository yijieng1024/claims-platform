package com.example.claims.claims;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** TEMPORARY: proves the auth wiring. Remove once real endpoints exist. */
@RestController
class WhoAmIController {

    record WhoAmI(String id, String name, String role, String market, List<String> authorities) {
    }

    @GetMapping("/api/claims/whoami")
    WhoAmI whoami(JwtAuthenticationToken auth) {
        var jwt = auth.getToken();
        return new WhoAmI(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("role"),
                jwt.getClaimAsString("market"),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
