package com.coralio.expense_management_microservice.services;

import com.coralio.expense_management_microservice.client.UserServiceClient;
import com.coralio.expense_management_microservice.dto.CategoryDTO;
import com.coralio.expense_management_microservice.dto.ProjectResponseDTO;
import com.coralio.expense_management_microservice.entities.PaymentOrder;
import com.coralio.expense_management_microservice.entities.PaymentOrderStatus;
import com.coralio.expense_management_microservice.entities.ReimbursedLine;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);

    // Couleurs professionnelles
    private static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(0, 51, 102);
    private static final DeviceRgb COLOR_SECONDARY = new DeviceRgb(108, 117, 125);
    private static final DeviceRgb COLOR_HEADER_BG = new DeviceRgb(240, 242, 245);
    private static final DeviceRgb COLOR_SUCCESS = new DeviceRgb(40, 167, 69);
    private static final DeviceRgb COLOR_DANGER = new DeviceRgb(220, 53, 69);
    private static final DeviceRgb COLOR_WARNING = new DeviceRgb(255, 193, 7);

    private final FileStorageService fileStorageService;
    private final UserServiceClient userServiceClient;
    private final CategoryService categoryService;
    private final ProjectService projectService;

    private final Map<String, String> employeeNameCache = new ConcurrentHashMap<>();
    private final Map<String, String> employeeEmailCache = new ConcurrentHashMap<>();
    private final Map<Long, String> categoryNameCache = new ConcurrentHashMap<>();
    private final Map<Long, String> projectNameCache = new ConcurrentHashMap<>();

    public PdfGenerationService(FileStorageService fileStorageService,
                                UserServiceClient userServiceClient,
                                CategoryService categoryService,
                                ProjectService projectService) {
        this.fileStorageService = fileStorageService;
        this.userServiceClient = userServiceClient;
        this.categoryService = categoryService;
        this.projectService = projectService;
    }

    public byte[] generatePaymentOrderPdf(PaymentOrder order) {
        log.info("📄 Génération du PDF pour l'ordre de paiement #{}", order.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);

            // Add footer event handler for all pages
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler());

            Document document = new Document(pdf);
            document.setMargins(20, 25, 60, 25); // Increased bottom margin for footer

            PdfFont font = PdfFontFactory.createFont("Helvetica");
            document.setFont(font);

            addCompanyHeader(document);
            addTitle(document, order);
            addReferences(document, order);
            addRecipientInfo(document, order);
            addOrderDetails(document, order);
            addItemsTable(document, order);
            addFinancialSummary(document, order);
            addSignatures(document);

            document.close();

            String fileName = String.format("ORDRE_%d_%s.pdf",
                    order.getId(),
                    order.getExpenseNote().getEmployeeId());

            fileStorageService.storePdf(baos.toByteArray(),
                    order.getExpenseNote().getEmployeeId(),
                    "payment_orders",
                    fileName);

            log.info("✅ PDF sauvegardé: {}", fileName);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Erreur lors de la génération du PDF", e);
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    private void addCompanyHeader(Document document) {
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1}))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER);

        Cell logoCell = new Cell();
        try {
            ClassPathResource imgResource = new ClassPathResource("static/images/coral-io logo.jpg");
            InputStream inputStream = imgResource.getInputStream();
            ImageData imageData = ImageDataFactory.create(inputStream.readAllBytes());
            Image logo = new Image(imageData);
            logo.setWidth(40);
            logo.setHeight(40);
            logoCell.add(logo);
        } catch (Exception e) {
            logoCell.add(new Paragraph("CORALIO")
                    .setBold()
                    .setFontSize(12)
                    .setFontColor(COLOR_PRIMARY));
        }
        logoCell.setBorder(Border.NO_BORDER);
        headerTable.addCell(logoCell);

        Cell companyCell = new Cell()
                .add(new Paragraph("CORALIO")
                        .setBold()
                        .setFontSize(14)
                        .setFontColor(COLOR_PRIMARY))
                .add(new Paragraph("Solutions Financières")
                        .setFontSize(8)
                        .setFontColor(COLOR_SECONDARY))
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER);
        headerTable.addCell(companyCell);

        Cell refCell = new Cell()
                .add(new Paragraph("DOCUMENT OFFICIEL")
                        .setBold()
                        .setFontSize(8)
                        .setFontColor(COLOR_PRIMARY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER);
        headerTable.addCell(refCell);

        document.add(headerTable);
        document.add(new Paragraph("__________________________________________________")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(COLOR_PRIMARY)
                .setFontSize(6));
        document.add(new Paragraph("\n"));
    }

    private void addTitle(Document document, PaymentOrder order) {
        Paragraph title = new Paragraph("ORDRE DE PAIEMENT N° " + order.getId())
                .setFontSize(12)
                .setBold()
                .setFontColor(COLOR_PRIMARY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        String status = getStatusText(order.getStatus());
        Paragraph statusPara = new Paragraph(status)
                .setFontSize(9)
                .setFontColor(getStatusColor(order.getStatus()))
                .setTextAlignment(TextAlignment.CENTER);
        document.add(statusPara);
        document.add(new Paragraph("\n"));
    }

    private void addReferences(Document document, PaymentOrder order) {
        Table refTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2}))
                .useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)
                .setFontSize(8);

        addRefRow(refTable, "Date d'émission:", formatDate(order.getCreatedAt()));
        addRefRow(refTable, "N° Note de frais:", "#" + order.getExpenseNote().getId());

        if (order.getPaymentDate() != null) {
            addRefRow(refTable, "Date de paiement:", formatDate(order.getPaymentDate()));
        }

        document.add(refTable);
        document.add(new Paragraph("\n"));
    }

    private void addRefRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold()).setBorder(Border.NO_BORDER));
        table.addCell(new Cell().add(new Paragraph(value)).setBorder(Border.NO_BORDER));
    }

    private void addRecipientInfo(Document document, PaymentOrder order) {
        String employeeId = order.getExpenseNote().getEmployeeId();
        String employeeName = getEmployeeName(employeeId);
        String employeeEmail = getEmployeeEmail(employeeId);

        Table recipientTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth()
                .setMarginTop(3)
                .setMarginBottom(3);

        Cell headerCell = new Cell(1, 2)
                .add(new Paragraph("BÉNÉFICIAIRE")
                        .setBold()
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY))
                .setBackgroundColor(COLOR_HEADER_BG)
                .setBorder(new SolidBorder(COLOR_PRIMARY, 1));
        recipientTable.addCell(headerCell);

        addInfoRow(recipientTable, "Nom:", employeeName != null ? employeeName : "Non disponible");
        addInfoRow(recipientTable, "ID Employé:", employeeId);
        if (employeeEmail != null) {
            addInfoRow(recipientTable, "Email:", employeeEmail);
        }

        Long projectId = order.getExpenseNote().getProjectId();
        if (projectId != null) {
            String projectName = getProjectName(projectId);
            addInfoRow(recipientTable, "Projet:", projectName != null ? projectName : "Projet #" + projectId);
        }

        document.add(recipientTable);
        document.add(new Paragraph("\n"));
    }

    private void addInfoRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold()).setBorder(Border.NO_BORDER).setFontSize(8));
        table.addCell(new Cell().add(new Paragraph(value)).setBorder(Border.NO_BORDER).setFontSize(8));
    }

    private void addOrderDetails(Document document, PaymentOrder order) {
        Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth()
                .setMarginTop(3)
                .setMarginBottom(3);

        Cell headerCell = new Cell(1, 2)
                .add(new Paragraph("DÉTAILS DE L'ORDRE")
                        .setBold()
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY))
                .setBackgroundColor(COLOR_HEADER_BG)
                .setBorder(new SolidBorder(COLOR_PRIMARY, 1));
        detailsTable.addCell(headerCell);

        addInfoRow(detailsTable, "Méthode de paiement:",
                order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "À déterminer");

        if (order.getPaymentReference() != null && !order.getPaymentReference().isEmpty()) {
            addInfoRow(detailsTable, "Référence:", order.getPaymentReference());
        }

        if (order.getAdminComment() != null && !order.getAdminComment().isEmpty()) {
            addInfoRow(detailsTable, "Commentaire:", order.getAdminComment());
        }

        document.add(detailsTable);
        document.add(new Paragraph("\n"));
    }

    private void addItemsTable(Document document, PaymentOrder order) {
        Paragraph tableTitle = new Paragraph("DÉTAIL DES REMBOURSEMENTS")
                .setBold()
                .setFontSize(10)
                .setFontColor(COLOR_PRIMARY);
        document.add(tableTitle);

        String displayCurrency = order.getDisplayCurrency() != null ? order.getDisplayCurrency() : "TND";
        Double exchangeRate = order.getExchangeRate() != null ? order.getExchangeRate() : 1.0;
        String currencySymbol = getCurrencySymbol(displayCurrency);

        Table table = new Table(UnitValue.createPercentArray(new float[]{2.5f, 2, 1.2f, 1.2f, 1, 1.5f}))
                .useAllAvailableWidth()
                .setMarginTop(3)
                .setMarginBottom(3);

        String[] headers = {"Description", "Catégorie", "Montant (" + currencySymbol + ")", "Remboursé (" + currencySymbol + ")", "Taux", "Obs."};
        for (String header : headers) {
            Cell headerCell = new Cell()
                    .add(new Paragraph(header).setBold().setFontSize(7))
                    .setBackgroundColor(COLOR_HEADER_BG)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBorder(new SolidBorder(COLOR_SECONDARY, 0.5f));
            table.addHeaderCell(headerCell);
        }

        for (ReimbursedLine line : order.getReimbursedLines()) {
            String description = line.getExpenseLine().getDescription();
            table.addCell(new Cell().add(new Paragraph(description != null && description.length() > 30 ? description.substring(0, 27) + "..." : (description != null ? description : "-")).setFontSize(7)));

            Long categoryId = line.getExpenseLine().getCategoryId();
            String categoryName = getCategoryName(categoryId);
            table.addCell(new Cell().add(new Paragraph(categoryName != null && categoryName.length() > 20 ? categoryName.substring(0, 17) + "..." : (categoryName != null ? categoryName : "-")).setFontSize(7)));

            double originalInDisplayCurrency = line.getOriginalAmount() * exchangeRate;
            table.addCell(new Cell().add(new Paragraph(formatCurrency(originalInDisplayCurrency, displayCurrency)))
                    .setTextAlignment(TextAlignment.RIGHT).setFontSize(7));

            double reimbursedInDisplayCurrency = line.getReimbursedAmount() * exchangeRate;

            Cell amountCell = new Cell()
                    .add(new Paragraph(formatCurrency(reimbursedInDisplayCurrency, displayCurrency)))
                    .setTextAlignment(TextAlignment.RIGHT).setFontSize(7);

            if (line.getIsFullyReimbursed()) {
                amountCell.setFontColor(COLOR_SUCCESS);
            } else if (line.getReimbursedAmount() == 0) {
                amountCell.setFontColor(COLOR_DANGER);
            } else {
                amountCell.setFontColor(COLOR_WARNING);
            }
            table.addCell(amountCell);

            String type = line.getIsFullyReimbursed() ? "100%" :
                    (line.getReimbursedAmount() > 0 ?
                            String.format("%.0f%%", (line.getReimbursedAmount() / line.getOriginalAmount()) * 100) : "0%");
            Cell typeCell = new Cell().add(new Paragraph(type)).setTextAlignment(TextAlignment.CENTER).setFontSize(7);
            if ("100%".equals(type)) {
                typeCell.setBackgroundColor(COLOR_SUCCESS).setFontColor(ColorConstants.WHITE);
            } else if ("0%".equals(type)) {
                typeCell.setBackgroundColor(COLOR_DANGER).setFontColor(ColorConstants.WHITE);
            } else {
                typeCell.setBackgroundColor(COLOR_WARNING).setFontColor(ColorConstants.BLACK);
            }
            table.addCell(typeCell);

            String comment = line.getAdminComment();
            table.addCell(new Cell().add(new Paragraph(comment != null && comment.length() > 15 ? comment.substring(0, 12) + "..." : (comment != null ? comment : "-")).setFontSize(7)));
        }

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void addFinancialSummary(Document document, PaymentOrder order) {
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth()
                .setMarginTop(3);

        Cell summaryHeader = new Cell(1, 2)
                .add(new Paragraph("RÉCAPITULATIF FINANCIER")
                        .setBold()
                        .setFontSize(10)
                        .setFontColor(COLOR_PRIMARY))
                .setBackgroundColor(COLOR_HEADER_BG)
                .setBorder(new SolidBorder(COLOR_PRIMARY, 1));
        summaryTable.addCell(summaryHeader);

        String displayCurrency = order.getDisplayCurrency() != null ? order.getDisplayCurrency() : "TND";
        Double exchangeRate = order.getExchangeRate() != null ? order.getExchangeRate() : 1.0;
        Double totalInDisplayCurrency = order.getTotalAmount() * exchangeRate;

        summaryTable.addCell(new Cell().add(new Paragraph("Montant total remboursé:").setBold()).setBorder(Border.NO_BORDER).setFontSize(8));
        summaryTable.addCell(new Cell().add(new Paragraph(formatCurrency(totalInDisplayCurrency, displayCurrency)).setBold().setFontColor(COLOR_SUCCESS))
                .setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setFontSize(8));

        String amountInWords = convertAmountToWords(totalInDisplayCurrency, displayCurrency);
        summaryTable.addCell(new Cell(1, 2)
                .add(new Paragraph("Arrêté la présente somme à : " + amountInWords).setFontSize(7).setFontColor(COLOR_SECONDARY))
                .setBorder(Border.NO_BORDER));

        if (!"TND".equals(displayCurrency) && exchangeRate != 1.0) {
            summaryTable.addCell(new Cell(1, 2)
                    .add(new Paragraph("(Taux de change utilisé: 1 TND = " +
                            String.format("%.4f", exchangeRate) + " " + displayCurrency + ")")
                            .setFontSize(6).setFontColor(COLOR_SECONDARY))
                    .setBorder(Border.NO_BORDER));
        }

        document.add(summaryTable);
        document.add(new Paragraph("\n"));
    }

    private String formatCurrency(double amount, String currencyCode) {
        String symbol = getCurrencySymbol(currencyCode);
        int decimals = getCurrencyDecimals(currencyCode);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');

        String pattern = "#,##0";
        if (decimals > 0) {
            pattern += "." + "0".repeat(decimals);
        }

        DecimalFormat formatter = new DecimalFormat(pattern, symbols);
        formatter.setMinimumFractionDigits(decimals);
        formatter.setMaximumFractionDigits(decimals);

        String formattedAmount = formatter.format(amount);

        return formattedAmount + " " + symbol;
    }

    private String getCurrencySymbol(String currencyCode) {
        switch (currencyCode) {
            case "TND": return "DT";
            case "EUR": return "€";
            case "USD": return "$";
            default: return currencyCode;
        }
    }

    private int getCurrencyDecimals(String currencyCode) {
        switch (currencyCode) {
            case "TND": return 3;
            case "EUR":
            case "USD": return 2;
            default: return 2;
        }
    }

    private String convertAmountToWords(double amount, String currencyCode) {
        int wholeUnit = (int) amount;
        int decimals = getCurrencyDecimals(currencyCode);
        int fractional = (int) Math.round((amount - wholeUnit) * Math.pow(10, decimals));

        String unitName, fractionName;
        switch (currencyCode) {
            case "TND":
                unitName = wholeUnit > 1 ? "dinars" : "dinar";
                fractionName = fractional > 1 ? "millimes" : "millime";
                break;
            case "EUR":
                unitName = wholeUnit > 1 ? "euros" : "euro";
                fractionName = fractional > 1 ? "centimes" : "centime";
                break;
            case "USD":
                unitName = wholeUnit > 1 ? "dollars" : "dollar";
                fractionName = fractional > 1 ? "cents" : "cent";
                break;
            default:
                unitName = wholeUnit > 1 ? "unités" : "unité";
                fractionName = fractional > 1 ? "centièmes" : "centième";
        }

        if (fractional > 0) {
            return wholeUnit + " " + unitName + " et " + fractional + " " + fractionName;
        }
        return wholeUnit + " " + unitName;
    }

    private void addSignatures(Document document) {
        Table signatureTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginTop(10);

        signatureTable.addCell(new Cell()
                .add(new Paragraph("Le demandeur\n\n_________________________")
                        .setTextAlignment(TextAlignment.CENTER))
                .setBorder(Border.NO_BORDER));

        signatureTable.addCell(new Cell()
                .add(new Paragraph("Le responsable financier\n\n_________________________")
                        .setTextAlignment(TextAlignment.CENTER))
                .setBorder(Border.NO_BORDER));

        document.add(signatureTable);
    }

    // ========== FOOTER EVENT HANDLER ==========

    private class FooterEventHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();

            try {
                PdfFont font = PdfFontFactory.createFont("Helvetica");

                // Create PdfCanvas first
                PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);

                // Position at bottom of page (20 points from bottom)
                float y = pageSize.getBottom() + 20;
                float leftX = pageSize.getLeft() + 25;
                float rightX = pageSize.getRight() - 25;

                // Begin text
                pdfCanvas.beginText();
                pdfCanvas.setFontAndSize(font, 6);
                pdfCanvas.setFillColor(COLOR_SECONDARY);

                // Left text
                pdfCanvas.moveText(leftX, y);
                pdfCanvas.showText("Document généré automatiquement");

                // Right text
                String dateStr = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                pdfCanvas.moveText(rightX - leftX - 100, 0); // Adjust position for right text
                pdfCanvas.showText(dateStr);

                // End text
                pdfCanvas.endText();
                pdfCanvas.release();

            } catch (Exception e) {
                log.error("Error adding footer to page", e);
            }
        }
    }
    // ========== MÉTHODES UTILITAIRES ==========

    private String formatDate(java.time.LocalDateTime date) {
        if (date == null) return "-";
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String getStatusText(PaymentOrderStatus status) {
        if (status == null) return "Inconnu";
        switch (status) {
            case EN_ATTENTE: return "En attente de validation";
            case PAYE: return "Payé";
            case ANNULE: return "Annulé";
            default: return status.getDisplayName();
        }
    }

    private DeviceRgb getStatusColor(PaymentOrderStatus status) {
        switch (status) {
            case EN_ATTENTE: return COLOR_WARNING;
            case PAYE: return COLOR_SUCCESS;
            case ANNULE: return COLOR_DANGER;
            default: return COLOR_SECONDARY;
        }
    }

    private String getEmployeeName(String employeeId) {
        return employeeNameCache.computeIfAbsent(employeeId, id -> {
            try {
                return userServiceClient.getUserName(id);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private String getEmployeeEmail(String employeeId) {
        return employeeEmailCache.computeIfAbsent(employeeId, id -> {
            try {
                return userServiceClient.getUserEmail(id);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) return "Non catégorisé";
        return categoryNameCache.computeIfAbsent(categoryId, id -> {
            try {
                CategoryDTO category = categoryService.getCategoryById(id);
                return category != null ? category.getName() : "Catégorie #" + id;
            } catch (Exception e) {
                return "Catégorie #" + id;
            }
        });
    }

    private String getProjectName(Long projectId) {
        if (projectId == null) return null;
        return projectNameCache.computeIfAbsent(projectId, id -> {
            try {
                return projectService.getProjectById(id)
                        .map(ProjectResponseDTO::getName)
                        .orElse("Projet #" + id);
            } catch (Exception e) {
                return "Projet #" + id;
            }
        });
    }
}