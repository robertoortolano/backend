package com.example.demo.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class per operazioni CSV comuni
 */
public class CsvUtils {
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Escapa un valore per CSV secondo le regole RFC 4180
     * 
     * @param value Valore da escapare
     * @return Valore escapato, pronto per essere inserito in CSV
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Se il valore contiene virgole, virgolette o newline, lo racchiudiamo tra virgolette
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Crea una ResponseEntity per download CSV con headers appropriati
     * 
     * @param csvContent Contenuto CSV come stringa
     * @param filenamePrefix Prefisso del nome file (es: "fieldset_removal_impact")
     * @param entityId ID dell'entità (per il nome file)
     * @return ResponseEntity con CSV pronto per download
     */
    public static ResponseEntity<byte[]> createCsvResponse(
            String csvContent, 
            String filenamePrefix, 
            Long entityId) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String filename = String.format("%s_%d_%s.csv", filenamePrefix, entityId, timestamp);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(csvContent.length());
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }
}

