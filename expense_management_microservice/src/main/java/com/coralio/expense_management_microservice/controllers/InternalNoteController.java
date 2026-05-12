package com.coralio.expense_management_microservice.controllers;

import com.coralio.expense_management_microservice.dto.InternalNoteDto;
import com.coralio.expense_management_microservice.dto.InternalNoteRequest;
import com.coralio.expense_management_microservice.entities.ExpenseNoteInternalHistory;
import com.coralio.expense_management_microservice.services.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/expenses/{noteId}/internal-notes")
@RequiredArgsConstructor
@Slf4j
public class InternalNoteController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<InternalNoteDto>> getInternalHistory(@PathVariable Long noteId) {

        List<ExpenseNoteInternalHistory> history = expenseService.getInternalHistory(noteId);

        List<InternalNoteDto> dtos = history.stream()
                .map(h -> new InternalNoteDto(
                        h.getId(),
                        h.getExpenseNoteId(),
                        h.getAuthorId(),
                        h.getAuthorName(),
                        h.getAuthorRole(),
                        h.getContent(),
                        h.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> addInternalNote(
            @PathVariable Long noteId,
            @RequestBody InternalNoteRequest request) {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String authorId = authentication.getName();

            // 🔍 LOG des autorités Spring (pour diagnostic)
            log.info("🔐 Autorités Spring de l'utilisateur {} : {}", authorId, authentication.getAuthorities());

            // ✅ Solution 2 : extraire les rôles directement du JWT (claim "realm_access.roles")
            boolean isAdmin = false;
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null && realmAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) realmAccess.get("roles");
                    isAdmin = roles != null && roles.contains("ADMIN");
                    log.info("🔑 Rôles Keycloak trouvés : {}", roles);
                }
            }

            // Fallback : si pas de Jwt ou roles introuvables, utiliser l'ancienne méthode
            if (!isAdmin) {
                isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") || auth.getAuthority().equals("ADMIN"));
            }

            String role = isAdmin ? "ADMIN" : "MANAGER";
            log.info("✅ Rôle attribué pour {} : {}", authorId, role);

            String authorName = request.getAuthorName();
            if (authorName == null || authorName.isBlank()) {
                authorName = isAdmin ? "Admin" : authorId;
            }

            ExpenseNoteInternalHistory created = expenseService.addInternalNote(
                    noteId,
                    authorId,
                    authorName,
                    role,
                    request.getContent()
            );

            InternalNoteDto dto = new InternalNoteDto(
                    created.getId(),
                    created.getExpenseNoteId(),
                    created.getAuthorId(),
                    created.getAuthorName(),
                    created.getAuthorRole(),
                    created.getContent(),
                    created.getCreatedAt()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Erreur lors de l'ajout de la note interne pour noteId={}", noteId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}