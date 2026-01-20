package com.coralio.gatewayserver.security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig
{

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        String jwkSetUri = "http://localhost:8090/realms/master/protocol/openid-connect/certs";
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        http.authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**").hasRole("ADMIN")
                        .pathMatchers("/api/expenses/**").hasRole("ADMIN")
                        .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
                );

        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

}


