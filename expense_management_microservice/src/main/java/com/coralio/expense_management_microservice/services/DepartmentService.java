package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.DepartmentRequestDTO;
import com.coralio.expense_management_microservice.dto.DepartmentResponseDTO;
import com.coralio.expense_management_microservice.entities.Department;
import com.coralio.expense_management_microservice.enums.DepartmentStatus;
import com.coralio.expense_management_microservice.repos.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    // ==================== CONVERSION ====================

    private DepartmentResponseDTO convertToResponseDTO(Department department) {
        DepartmentResponseDTO dto = new DepartmentResponseDTO();
        dto.setId(department.getId());
        dto.setName(department.getName());
        dto.setCode(department.getCode());
        dto.setStatus(department.getStatus());
        dto.setDescription(department.getDescription());
        dto.setEmail(department.getEmail());
        dto.setPhone(department.getPhone());
        dto.setLocation(department.getLocation());
        dto.setCreatedAt(department.getCreatedAt());
        dto.setUpdatedAt(department.getUpdatedAt());
        return dto;
    }

    // ==================== CRUD OPERATIONS ====================

    @Transactional
    public DepartmentResponseDTO createDepartment(DepartmentRequestDTO request) {
        // Vérifier si le code existe déjà
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Un département avec ce code existe déjà");
        }

        // Vérifier si le nom existe déjà
        if (departmentRepository.existsByName(request.getName())) {
            throw new RuntimeException("Un département avec ce nom existe déjà");
        }

        Department department = new Department();
        department.setName(request.getName());
        department.setCode(request.getCode());
        department.setStatus(request.getStatus() != null ? request.getStatus() : DepartmentStatus.ACTIF);
        department.setDescription(request.getDescription());
        department.setEmail(request.getEmail());
        department.setPhone(request.getPhone());
        department.setLocation(request.getLocation());

        Department savedDepartment = departmentRepository.save(department);
        return convertToResponseDTO(savedDepartment);
    }

    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentRepository.findAll()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public Optional<DepartmentResponseDTO> getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .map(this::convertToResponseDTO);
    }

    public Optional<Department> getDepartmentEntityById(Long id) {
        return departmentRepository.findById(id);
    }

    @Transactional
    public DepartmentResponseDTO updateDepartment(Long id, DepartmentRequestDTO request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Département non trouvé avec l'id: " + id));

        // Vérifier si le code existe déjà pour un autre département
        if (departmentRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new RuntimeException("Un autre département avec ce code existe déjà");
        }

        // Vérifier si le nom existe déjà pour un autre département
        if (departmentRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new RuntimeException("Un autre département avec ce nom existe déjà");
        }

        department.setName(request.getName());
        department.setCode(request.getCode());
        if (request.getStatus() != null) {
            department.setStatus(request.getStatus());
        }
        department.setDescription(request.getDescription());
        department.setEmail(request.getEmail());
        department.setPhone(request.getPhone());
        department.setLocation(request.getLocation());
        department.setUpdatedAt(LocalDateTime.now());

        Department updatedDepartment = departmentRepository.save(department);
        return convertToResponseDTO(updatedDepartment);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Département non trouvé avec l'id: " + id);
        }

        // Vérifier si le département est utilisé par des projets
        // À implémenter avec ProjectRepository

        departmentRepository.deleteById(id);
    }

    @Transactional
    public DepartmentResponseDTO updateDepartmentStatus(Long id, DepartmentStatus status) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Département non trouvé avec l'id: " + id));

        department.setStatus(status);
        department.setUpdatedAt(LocalDateTime.now());

        return convertToResponseDTO(departmentRepository.save(department));
    }

    // ==================== FILTRES ET RECHERCHE ====================

    public List<DepartmentResponseDTO> getDepartmentsByStatus(DepartmentStatus status) {
        return departmentRepository.findByStatus(status)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<DepartmentResponseDTO> searchDepartments(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllDepartments();
        }
        return departmentRepository.searchDepartments(searchTerm.trim())
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<DepartmentResponseDTO> getActiveDepartments() {
        return departmentRepository.findByStatus(DepartmentStatus.ACTIF)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
}