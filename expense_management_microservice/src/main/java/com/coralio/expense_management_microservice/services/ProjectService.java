package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.repos.ProjectRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project createProject(Project project) {
        return projectRepository.save(project);
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }
    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }



    public Project updateProject(Long id, Project projectDetails) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found with id " + id));
        project.setName(projectDetails.getName());
        project.setCode(projectDetails.getCode());
        return projectRepository.save(project);
    }

    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }
}
