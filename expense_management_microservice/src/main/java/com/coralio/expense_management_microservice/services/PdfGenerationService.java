package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.entities.PaymentOrder;
import com.coralio.expense_management_microservice.entities.ReimbursedLine;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);
    private final FileStorageService fileStorageService;

    public PdfGenerationService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Génère un PDF pour un ordre de paiement
     */
    public byte[] generatePaymentOrderPdf(PaymentOrder order) {
        log.info("Génération du PDF pour l'ordre de paiement #{}", order.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // En-tête
            Paragraph title = new Paragraph("ORDRE DE PAIEMENT")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold();
            document.add(title);
            document.add(new Paragraph("\n"));

            // Informations générales
            document.add(new Paragraph("N° Ordre : " + order.getId()).setFontSize(11));
            document.add(new Paragraph("Date de création : " +
                    order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));

            if (order.getPaymentDate() != null) {
                document.add(new Paragraph("Date de paiement : " +
                        order.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            }
            document.add(new Paragraph("\n"));

            // Informations employé
            document.add(new Paragraph("INFORMATIONS EMPLOYÉ").setBold().setFontSize(12));
            document.add(new Paragraph("ID Employé : " + order.getExpenseNote().getEmployeeId()));
            document.add(new Paragraph("Note de frais N° : " + order.getExpenseNote().getId()));
            document.add(new Paragraph("\n"));

            // Informations paiement
            document.add(new Paragraph("INFORMATIONS DE PAIEMENT").setBold().setFontSize(12));
            document.add(new Paragraph("Méthode de paiement : " +
                    (order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Non spécifiée")));

            if (order.getPaymentReference() != null && !order.getPaymentReference().isEmpty()) {
                document.add(new Paragraph("Référence : " + order.getPaymentReference()));
            }

            if (order.getAdminComment() != null && !order.getAdminComment().isEmpty()) {
                document.add(new Paragraph("Commentaire admin : " + order.getAdminComment()));
            }
            document.add(new Paragraph("\n"));

            // Tableau des lignes remboursées
            document.add(new Paragraph("DÉTAIL DES REMBOURSEMENTS").setBold().setFontSize(12));

            Table table = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 1.5f, 1.5f}))
                    .useAllAvailableWidth();

            table.addHeaderCell("Description");
            table.addHeaderCell("Catégorie");
            table.addHeaderCell("Montant original");
            table.addHeaderCell("Montant remboursé");
            table.addHeaderCell("Type");

            for (ReimbursedLine line : order.getReimbursedLines()) {
                table.addCell(line.getExpenseLine().getDescription() != null ?
                        line.getExpenseLine().getDescription() : "-");

                table.addCell("Catégorie " + line.getExpenseLine().getCategoryId());

                table.addCell(String.format("%.2f €", line.getOriginalAmount()));
                table.addCell(String.format("%.2f €", line.getReimbursedAmount()));
                table.addCell(line.getIsFullyReimbursed() ? "Total" : "Partiel");
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // Total
            Paragraph total = new Paragraph(
                    String.format("TOTAL REMBOURSÉ : %.2f €", order.getTotalAmount())
            )
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(14)
                    .setBold();
            document.add(total);

            document.add(new Paragraph("\n\n\n"));

            // Signatures
            document.add(new Paragraph("Cachet de l'entreprise : _________________________")
                    .setMarginTop(30));

            document.add(new Paragraph("Signature du responsable : _________________________")
                    .setMarginTop(20));

            // Pied de page
            document.add(new Paragraph("\n"));
            document.add(new Paragraph(
                    "Document généré automatiquement le " +
                            java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            ).setFontSize(8).setTextAlignment(TextAlignment.CENTER));

            document.close();

            // Sauvegarder le PDF
            String fileName = String.format("payment_order_%d_%s.pdf",
                    order.getId(),
                    order.getExpenseNote().getEmployeeId());

            fileStorageService.storePdf(baos.toByteArray(),
                    order.getExpenseNote().getEmployeeId(),
                    "payment_orders",
                    fileName);

            log.info("PDF sauvegardé: {}", fileName);

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF pour l'ordre #{}", order.getId(), e);
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }
}