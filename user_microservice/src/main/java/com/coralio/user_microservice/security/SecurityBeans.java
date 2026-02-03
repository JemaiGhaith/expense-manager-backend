package com.coralio.user_microservice.security;//package com.coralio.expense_management_microservice.security;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.jwt.JwtDecoder;
//import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
//
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
//
//@Configuration
//public class SecurityBeans {
//
//    private static final String SECRET = "coralio@yahoo.com"; // même que dans SecParams
//
//    @Bean
//    public JwtDecoder jwtDecoder() {
//        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
//        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(keyBytes, "HmacSHA256")).build();
//    }
//}
