package com.coralio.user_microservice.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DepartmentService {

    private final RestTemplate restTemplate;
    private final String expenseServiceUrl = "http://localhost:8082/api/departments";

    public DepartmentService() {
        this.restTemplate = new RestTemplate();
    }

    public String getDepartmentName(Long departmentId) {
        try {
            // Appeler le microservice expense pour récupérer le département
            String url = expenseServiceUrl + "/" + departmentId;
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