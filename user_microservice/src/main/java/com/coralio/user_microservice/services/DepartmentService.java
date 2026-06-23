package com.coralio.user_microservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DepartmentService {

    private final RestTemplate restTemplate;
    @Value("${expense.service.url:http://localhost:8082}")
    private String expenseServiceUrl;
    public DepartmentService() {
        this.restTemplate = new RestTemplate();
    }

    public String getDepartmentName(Long departmentId) {
        try {
            // Appeler le microservice expense pour récupérer le département
            String url = expenseServiceUrl + "/api/departments/" + departmentId;
            DepartmentDto department = restTemplate.getForObject(url, DepartmentDto.class);
            return department != null ? department.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // DTO interne
    private static class DepartmentDto {
        private Long id;
        private String name;
        private String code;

        public String getName() { return name; }
    }
}