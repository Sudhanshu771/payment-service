package com.payrecon.payment.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// POST /api/v1/auth/login  {"username": "...", "password": "..."}
//   correct → 200 + a JWT valid for a limited time
//   wrong   → 401 "Invalid username or password" (see GlobalExceptionHandler)
// Logout needs no endpoint: tokens are stateless, so the browser simply forgets its token.
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    public record LoginResponse(String token, String username, List<String> roles, Instant expiresAt) { }

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final Duration tokenLifetime;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                          @Value("${app.security.token-lifetime}") Duration tokenLifetime) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.tokenLifetime = tokenLifetime;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // 1. Check username + password (BCrypt compare). Throws AuthenticationException if wrong.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        // 2. "ROLE_AUDITOR" → "AUDITOR"  (only roles: Spring also adds e.g. "FACTOR_PASSWORD" = how you logged in)
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();

        // 3. Build and sign the token
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenLifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("payrecon")
                .subject(auth.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, auth.getName(), roles, expiresAt);
    }
}
