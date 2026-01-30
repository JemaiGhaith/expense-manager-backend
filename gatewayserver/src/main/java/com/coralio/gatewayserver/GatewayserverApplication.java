package com.coralio.gatewayserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class GatewayserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayserverApplication.class, args);
    }
/*@Bean
    public RouteLocator MyRouteConfig(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route(p -> p.path("/api/expenses/**")
                        .uri("lb://EXPENSE_MANAGEMENT_MICROSERVICE"))
                .route(p -> p.path("/api/auth/**")
                        .uri("lb://AUTH_MICROSERVICE"))
                .build();
    }
*/
}
