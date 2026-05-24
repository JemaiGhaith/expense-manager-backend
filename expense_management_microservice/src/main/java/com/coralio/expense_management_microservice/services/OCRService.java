package com.coralio.expense_management_microservice.services;
/*package com.coralio.ai_microservice.services;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.File;

@Service
public class OCRService {

    public String extractText(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            // Convertir en RGB si nécessaire
            BufferedImage convertedImg = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            convertedImg.getGraphics().drawImage(image, 0, 0, null);

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("C:\\Program Files\\tessdata"); // chemin vers ton dossier tessdata
            tesseract.setLanguage("eng");

            return tesseract.doOCR(convertedImg);
        } catch (Exception e) {
            throw new RuntimeException("Erreur OCR: " + e.getMessage(), e);
        }
    }
}*/
import net.sourceforge.tess4j.Tesseract;
import org.apache.tika.Tika;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
public class OCRService {
    @Value("${ocr.tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tesseractDataPath;
    public String extractText(MultipartFile file) {

        try {

            String name = file.getOriginalFilename().toLowerCase();

            // Images
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {

                BufferedImage image = ImageIO.read(file.getInputStream());

                Tesseract tesseract = new Tesseract();
                tesseract.setDatapath(tesseractDataPath); // dossier contenant .traineddata

                return tesseract.doOCR(image);
            }

            // PDF
            if (name.endsWith(".pdf")) {

                PDDocument document = PDDocument.load(file.getInputStream());
                PDFRenderer renderer = new PDFRenderer(document);

                StringBuilder text = new StringBuilder();

                Tesseract tesseract = new Tesseract();
                tesseract.setDatapath(tesseractDataPath); // dossier contenant .traineddata
                tesseract.setLanguage("eng+fra");

                for (int i = 0; i < document.getNumberOfPages(); i++) {

                    BufferedImage image = renderer.renderImage(i);

                    text.append(tesseract.doOCR(image));
                }

                document.close();

                return text.toString();
            }

            // Tous les autres fichiers
            Tika tika = new Tika();
            return tika.parseToString(file.getInputStream());

        } catch (Exception e) {
            throw new RuntimeException("Erreur extraction texte : " + e.getMessage());
        }
    }
}