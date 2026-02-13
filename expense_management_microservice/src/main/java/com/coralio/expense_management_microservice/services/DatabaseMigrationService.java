package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.dto.ColumnInfoDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DatabaseMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * ✅ Récupère les colonnes dynamiques AVEC LEUR TYPE DEPUIS category_fields !
     */
    public List<ColumnInfoDTO> getDynamicColumnsWithType() {
        // 1️⃣ Récupérer toutes les colonnes de expense_lines
        String sqlColumns = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_name = 'expense_lines'
            AND column_name NOT IN ('id', 'expense_note_id', 'category_id', 'amount', 
                                   'expense_date', 'description', 'justificatif_path',
                                   'created_at', 'updated_at')
            ORDER BY column_name
        """;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sqlColumns);

        // 2️⃣ Récupérer les TYPES depuis category_fields
        String sqlFields = """
            SELECT DISTINCT field_name, field_type, field_options
            FROM category_fields
            WHERE field_name IS NOT NULL
        """;

        Map<String, Map<String, Object>> fieldTypes = jdbcTemplate.queryForList(sqlFields)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("field_name"),
                        row -> row,
                        (existing, replacement) -> existing
                ));

        // 3️⃣ Combiner les informations
        return columns.stream()
                .map(col -> {
                    String columnName = (String) col.get("column_name");
                    String dataType = (String) col.get("data_type");

                    // ✅ Chercher le type dans category_fields
                    Map<String, Object> fieldInfo = fieldTypes.get(columnName);
                    String fieldType;
                    String fieldOptions = null;
                    boolean hasOptions = false;

                    if (fieldInfo != null) {
                        // ✅ On a le type exact depuis category_fields !
                        fieldType = (String) fieldInfo.get("field_type");
                        fieldOptions = (String) fieldInfo.get("field_options");
                        hasOptions = fieldOptions != null && !fieldOptions.isEmpty();
                        System.out.println("✅ Champ " + columnName + " trouvé dans category_fields: " + fieldType);
                    } else {
                        // ✅ Fallback: deviner par le nom
                        fieldType = guessFieldTypeFromName(columnName);
                        System.out.println("⚠️ Champ " + columnName + " non trouvé dans category_fields, deviné: " + fieldType);
                    }

                    // ✅ Compter combien de catégories utilisent ce champ
                    Long usageCount = getFieldUsageCount(columnName);

                    return ColumnInfoDTO.builder()
                            .columnName(columnName)
                            .dataType(dataType)
                            .fieldType(fieldType)
                            .hasOptions(hasOptions)
                            .fieldOptions(fieldOptions)
                            .usageCount(usageCount)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ Compter le nombre de catégories qui utilisent ce champ
     */
    private Long getFieldUsageCount(String fieldName) {
        String sql = """
            SELECT COUNT(DISTINCT category_id) 
            FROM category_fields 
            WHERE field_name = ?
        """;
        return jdbcTemplate.queryForObject(sql, Long.class, fieldName);
    }

    /**
     * ✅ Deviner le type par le nom (fallback)
     */
    private String guessFieldTypeFromName(String columnName) {
        String name = columnName.toLowerCase();
        if (name.contains("nuit") || name.contains("personne") ||
                name.contains("km") || name.contains("prix") ||
                name.contains("montant") || name.contains("cout") ||
                name.contains("plafond") || name.contains("nombre")) {
            return "NUMBER";
        }
        if (name.contains("date")) {
            return "DATE";
        }
        if (name.contains("type") || name.contains("categorie") ||
                name.contains("mode") || name.contains("option") ||
                name.contains("statut") || name.contains("etat")) {
            return "SELECT";
        }
        if (name.contains("description") || name.contains("commentaire") ||
                name.contains("detail") || name.contains("remarque")) {
            return "TEXTAREA";
        }
        return "TEXT";
    }

    // ✅ Garder les anciennes méthodes pour compatibilité
    public List<String> getDynamicColumns() {
        String sql = """
            SELECT column_name 
            FROM information_schema.columns 
            WHERE table_name = 'expense_lines'
            AND column_name NOT IN ('id', 'expense_note_id', 'category_id', 'amount', 
                                   'expense_date', 'description', 'justificatif_path',
                                   'created_at', 'updated_at')
            ORDER BY column_name
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("column_name"));
    }


    public boolean columnExists(String columnName) {
        String sql = """
            SELECT COUNT(*) 
            FROM information_schema.columns 
            WHERE table_name = 'expense_lines' 
            AND column_name = ?
        """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                columnName.toLowerCase()
        );
        return count != null && count > 0;
    }

    /**
     * Ajoute une colonne à la table expense_lines
     */
    public void addColumn(String columnName, String fieldType) {
        String sqlType = mapFieldTypeToSqlType(fieldType);
        String sql = String.format(
                "ALTER TABLE expense_lines ADD COLUMN %s %s",
                columnName.toLowerCase(),
                sqlType
        );

        jdbcTemplate.execute(sql);
        System.out.println("✅ Colonne ajoutée: " + columnName + " (" + sqlType + ")");
    }

    /**
     * Mappe le type du champ au type SQL
     */
    private String mapFieldTypeToSqlType(String fieldType) {
        return switch (fieldType.toUpperCase()) {
            case "NUMBER" -> "DECIMAL(10,2)";
            case "DATE" -> "DATE";
            case "SELECT", "TEXT", "TEXTAREA" -> "VARCHAR(500)";
            default -> "VARCHAR(255)";
        };
    }

    /**
     * Valide le nom de colonne (sécurité)
     */
    public boolean isValidColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            return false;
        }
        // ✅ Uniquement lettres, chiffres et underscore
        return columnName.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }

    /**
     * ✅ CORRIGÉ - Commentaires SQL valides pour PostgreSQL
     */
// ✅ CORRIGÉ - Récupérer TOUTES les colonnes sauf id
    public List<String> getAllColumns() {
        String sql = """
        SELECT column_name 
        FROM information_schema.columns 
        WHERE table_name = 'expense_lines'
        AND column_name != 'id'  
        ORDER BY ordinal_position
    """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("column_name"));
    }
}