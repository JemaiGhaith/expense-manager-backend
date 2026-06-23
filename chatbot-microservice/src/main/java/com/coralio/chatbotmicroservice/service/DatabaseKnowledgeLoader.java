package com.coralio.chatbotmicroservice.service;

import com.coralio.chatbotmicroservice.entity.Category;
import com.coralio.chatbotmicroservice.entity.CategoryFieldMapping;
import com.coralio.chatbotmicroservice.entity.ExpenseNote;
import com.coralio.chatbotmicroservice.repository.CategoryRepository;
import com.coralio.chatbotmicroservice.repository.ExpenseNoteRepository;
import com.coralio.chatbotmicroservice.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DatabaseKnowledgeLoader {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseNoteRepository expenseNoteRepository;

    @Autowired
    private VectorStore vectorStore;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void loadDatabaseToVectorStore() {
        log.info("📚 Loading knowledge from expense database to vector store...");

        List<Document> documents = new ArrayList<>();

        // 1. Load all active categories
        List<Category> categories = categoryRepository.findByActiveTrue();
        log.info("Found {} active categories", categories.size());

        for (Category category : categories) {
            String content = buildCategoryContent(category);
            Document doc = new Document(content);
            doc.getMetadata().put("type", "category");
            doc.getMetadata().put("category_id", category.getId() != null ? category.getId() : 0L);
            doc.getMetadata().put("category_name", category.getName() != null ? category.getName() : "Inconnu");
            doc.getMetadata().put("plafond", category.getPlafond() != null ? category.getPlafond() : 0.0);
            documents.add(doc);
            log.debug("Added category: {}", category.getName());
        }

        // 2. Load example approved notes
        List<ExpenseNote> approvedNotes = expenseNoteRepository.findByStatus("VALIDEE");
        for (ExpenseNote note : approvedNotes.stream().limit(20).toList()) {
            String content = buildExampleNoteContent(note);
            Document doc = new Document(content);
            doc.getMetadata().put("type", "example");
            doc.getMetadata().put("amount", note.getTotalAmount() != null ? note.getTotalAmount() : 0.0);
            documents.add(doc);
        }

        // 3. Add business rules
        documents.addAll(buildBusinessRules());

        // 4. Add to vector store
        vectorStore.add(documents);
        log.info("✅ Successfully loaded {} documents to vector store", documents.size());
    }

    private String buildCategoryContent(Category category) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("Catégorie: %s\n", category.getName()));
        content.append(String.format("Plafond: %.2f TND\n", category.getPlafond()));

        if (category.getDescription() != null) {
            content.append(String.format("Description: %s\n", category.getDescription()));
        }

        // ✅ CORRECTION : Utiliser fieldMappings au lieu de getFields()
        List<CategoryFieldMapping> mappings = category.getFieldMappings();
        if (mappings != null && !mappings.isEmpty()) {
            content.append("Champs:\n");
            for (CategoryFieldMapping mapping : mappings) {
                String fieldName = mapping.getField().getFieldName();
                String fieldType = mapping.getField().getFieldType();
                String required = mapping.isRequired() ? " [OBLIGATOIRE]" : " [optionnel]";
                content.append(String.format("- %s (%s)%s\n", fieldName, fieldType, required));
            }
        }

        return content.toString();
    }

    private String buildExampleNoteContent(ExpenseNote note) {
        return String.format(
                "Exemple de note approuvée #%d: Montant %.2f TND, créée le %s",
                note.getId(),
                note.getTotalAmount(),
                note.getFormattedDate() != null ? note.getFormattedDate() : "date inconnue"
        );
    }

    private List<Document> buildBusinessRules() {
        List<Document> rules = new ArrayList<>();

        rules.add(new Document(Constants.GENERAL_RULES));
        rules.add(new Document(Constants.DOCUMENT_RULES));
        rules.add(new Document(Constants.ALERT_RULES));
        rules.add(new Document(Constants.HOLIDAY_RULES));
        rules.add(new Document(Constants.WORKFLOW_RULES));

        return rules;
    }
}