package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.ProjectRequestDTO;
import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.repos.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ==================== CONVERSION ====================

    private ProjectResponseDTO convertToResponseDTO(Project project) {
        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setCode(project.getCode());
        dto.setDepartmentId(project.getDepartmentId());
        dto.setStatus(project.getStatus());
        dto.setDescription(project.getDescription());
        dto.setBudget(project.getBudget());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    // ==================== CRUD OPERATIONS ====================

    @Transactional
    public ProjectResponseDTO createProject(ProjectRequestDTO request) {
        // Vérifier si le code existe déjà
        if (projectRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Un projet avec ce code existe déjà");
        }

        // Valider les dates
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new RuntimeException("La date de fin doit être postérieure à la date de début");
            }
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDepartmentId(request.getDepartmentId());
        project.setStatus(request.getStatus() != null ? request.getStatus() : ProjectStatus.ACTIF);
        project.setDescription(request.getDescription());
        project.setBudget(request.getBudget());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());

        Project savedProject = projectRepository.save(project);
        return convertToResponseDTO(savedProject);
    }

    public List<ProjectResponseDTO> getAllProjects() {
        return projectRepository.findAll()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public Optional<ProjectResponseDTO> getProjectById(Long id) {
        return projectRepository.findById(id)
                .map(this::convertToResponseDTO);
    }

    public Optional<Project> getProjectEntityById(Long id) {
        return projectRepository.findById(id);
    }

    @Transactional
    public ProjectResponseDTO updateProject(Long id, ProjectRequestDTO request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + id));

        // Vérifier si le code existe déjà pour un autre projet
        if (projectRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new RuntimeException("Un autre projet avec ce code existe déjà");
        }

        // Valider les dates
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new RuntimeException("La date de fin doit être postérieure à la date de début");
            }
        }

        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDepartmentId(request.getDepartmentId());
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        project.setDescription(request.getDescription());
        project.setBudget(request.getBudget());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setUpdatedAt(LocalDateTime.now());

        Project updatedProject = projectRepository.save(project);
        return convertToResponseDTO(updatedProject);
    }

    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Projet non trouvé avec l'id: " + id);
        }
        projectRepository.deleteById(id);
    }

    @Transactional
    public ProjectResponseDTO updateProjectStatus(Long id, ProjectStatus status) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + id));

        project.setStatus(status);
        project.setUpdatedAt(LocalDateTime.now());

        return convertToResponseDTO(projectRepository.save(project));
    }

    // ==================== FILTRES ET RECHERCHE ====================

    public List<ProjectResponseDTO> getProjectsByStatus(ProjectStatus status) {
        return projectRepository.findByStatus(status)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> getProjectsByDepartment(Long departmentId) {
        return projectRepository.findByDepartmentId(departmentId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ProjectResponseDTO> searchProjects(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllProjects();
        }
        return projectRepository.searchProjects(searchTerm.trim())
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
}