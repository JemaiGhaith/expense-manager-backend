package com.coralio.expense_management_microservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
public class ExpenseManagementMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseManagementMicroserviceApplication.class, args);
    }

}