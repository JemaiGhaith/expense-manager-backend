package com.coralio.auth_microservice.security;

import com.coralio.auth_microservice.entities.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {

    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256); // clé secrète
    private final long expiration = 1000 * 60 * 60; // 1h

    // Génération du token avec rôle
    public String generateToken(User user) { // <-- User et non String
        return Jwts.builder()
                .setSubject(user.getUsername())              // OK, User a getUsername()
                .claim("roles", List.of(user.getRole().name())) // OK, User a getRole()
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key) // utilise la clé générée
                .compact();
    }


    // Récupérer username depuis le token
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Récupérer les rôles depuis le token
    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("roles", List.class);
    }

    // Vérifier la validité du token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
