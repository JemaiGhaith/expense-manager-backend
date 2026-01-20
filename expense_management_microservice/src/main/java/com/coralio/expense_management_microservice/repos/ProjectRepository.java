package com.coralio.expense_management_microservice.repos;


import com.coralio.expense_management_microservice.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {}
