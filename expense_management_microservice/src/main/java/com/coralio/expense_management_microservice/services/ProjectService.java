package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.ProjectRequestDTO;
import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.entities.ExpenseNote;
import com.coralio.expense_management_microservice.entities.ExpenseStatus;
import com.coralio.expense_management_microservice.entities.Project;
import com.coralio.expense_management_microservice.enums.ProjectStatus;
import com.coralio.expense_management_microservice.repos.EmployeeProjectAssignmentRepository;
import com.coralio.expense_management_microservice.repos.ExpenseNoteRepository;
import com.coralio.expense_management_microservice.repos.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EmployeeProjectService employeeProjectService;
    private final EmployeeProjectAssignmentRepository assignmentRepository;
    private final ExpenseNoteRepository expenseNoteRepository; // ✅ AJOUTER

    public ProjectService(ProjectRepository projectRepository,
                          EmployeeProjectService employeeProjectService,
                          EmployeeProjectAssignmentRepository assignmentRepository,
                          ExpenseNoteRepository expenseNoteRepository) { // ✅ AJOUTER
        this.projectRepository = projectRepository;
        this.employeeProjectService = employeeProjectService;
        this.assignmentRepository = assignmentRepository;
        this.expenseNoteRepository = expenseNoteRepository; // ✅ AJOUTER
    }

    // Conversion rendue publique pour l'utiliser ailleurs
    public ProjectResponseDTO convertToResponseDTO(Project project) {
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

    @Transactional
    public ProjectResponseDTO createProject(ProjectRequestDTO request) {
        // Vérifications existantes
        if (projectRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Un projet avec ce code existe déjà");
        }
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("La date de fin doit être postérieure à la date de début");
        }

        // Création du projet
        Project project = Project.builder()
                .name(request.getName())
                .code(request.getCode())
                .departmentId(request.getDepartmentId())
                .status(request.getStatus() != null ? request.getStatus() : ProjectStatus.ACTIF)
                .description(request.getDescription())
                .budget(request.getBudget())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Project savedProject = projectRepository.save(project);

        // Affectation des employés si fournis
        if (request.getEmployeeIds() != null && !request.getEmployeeIds().isEmpty()) {
            for (String employeeId : request.getEmployeeIds()) {
                employeeProjectService.assignEmployeeToProject(employeeId, savedProject.getId());
            }
        }

        return convertToResponseDTO(savedProject);
    }

    @Transactional
    public ProjectResponseDTO updateProject(Long id, ProjectRequestDTO request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + id));

        // Vérifications
        if (projectRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new RuntimeException("Un autre projet avec ce code existe déjà");
        }
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("La date de fin doit être postérieure à la date de début");
        }

        // Mise à jour des champs
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDepartmentId(request.getDepartmentId());
        if (request.getStatus() != null) project.setStatus(request.getStatus());
        project.setDescription(request.getDescription());
        project.setBudget(request.getBudget());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setUpdatedAt(LocalDateTime.now());

        Project updatedProject = projectRepository.save(project);

        // Gestion des affectations : si la liste est fournie, on remplace
        if (request.getEmployeeIds() != null) {
            // Supprimer toutes les anciennes affectations
            assignmentRepository.deleteByProjectId(updatedProject.getId());
            // Ajouter les nouvelles
            for (String employeeId : request.getEmployeeIds()) {
                employeeProjectService.assignEmployeeToProject(employeeId, updatedProject.getId());
            }
        }

        return convertToResponseDTO(updatedProject);
    }

    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Projet non trouvé avec l'id: " + id);
        }
        // Supprimer d'abord les affectations pour éviter les contraintes FK
        assignmentRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
    }

    // Méthodes de lecture existantes (inchangées)
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

    public ProjectResponseDTO updateProjectStatus(Long id, ProjectStatus status) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + id));
        project.setStatus(status);
        project.setUpdatedAt(LocalDateTime.now());
        return convertToResponseDTO(projectRepository.save(project));
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
    // ✅ NOUVELLE MÉTHODE : Récupérer la consommation du budget
    public Map<String, Object> getBudgetConsumed(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + projectId));

        Map<String, Object> response = new HashMap<>();
        response.put("budget", project.getBudget());

        // Détails par statut
        Map<String, Double> byStatus = new HashMap<>();
        byStatus.put("EN_ATTENTE", 0.0);
        byStatus.put("VALIDEE", 0.0);
        byStatus.put("REMBOURSEE", 0.0);
        byStatus.put("REFUSEE", 0.0);

        List<ExpenseNote> notes = expenseNoteRepository.findByProjectId(projectId);
        for (ExpenseNote note : notes) {
            if (note.getStatus() != null) {
                byStatus.merge(note.getStatus().name(), note.getTotalAmount(), Double::sum);
            }
        }

        response.put("byStatus", byStatus);

        // Total consommé (excluant REFUSEE)
        double consumed = byStatus.get("EN_ATTENTE") +
                byStatus.get("VALIDEE") +
                byStatus.get("REMBOURSEE");
        response.put("consumed", consumed);
        response.put("remaining", project.getBudget() - consumed);
        response.put("isOverBudget", consumed > project.getBudget());

        return response;
    }

    // ✅ Méthode pour obtenir juste le budget restant
    public Double getRemainingBudget(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Projet non trouvé avec l'id: " + projectId));

        Double totalExpenses = expenseNoteRepository
                .findByProjectId(projectId)
                .stream()
                .filter(note -> note.getStatus() == ExpenseStatus.EN_ATTENTE ||
                        note.getStatus() == ExpenseStatus.VALIDEE ||
                        note.getStatus() == ExpenseStatus.REMBOURSEE)
                .mapToDouble(ExpenseNote::getTotalAmount)
                .sum();

        return project.getBudget() - totalExpenses;
    }
}