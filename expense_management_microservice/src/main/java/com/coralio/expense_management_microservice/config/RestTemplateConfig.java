// expense-microservice/src/main/java/com/coralio/expense_management_microservice/config/RestTemplateConfig.java
package com.coralio.expense_management_microservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}