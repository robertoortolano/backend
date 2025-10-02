package com.example.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public class DefaultConfigLoader {

    // Costruttore privato per evitare istanziazione
    private DefaultConfigLoader() {}

    public static DefaultConfig load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = DefaultConfigLoader.class.getClassLoader().getResourceAsStream("default-config.json");
            if (is == null) {
                throw new DefaultConfigLoadException("File default-config.json non trovato nel classpath");
            }
            return mapper.readValue(is, DefaultConfig.class);
        } catch (Exception e) {
            throw new DefaultConfigLoadException("Errore caricando default-config.json", e);
        }
    }

    public static class DefaultConfigLoadException extends RuntimeException {
        public DefaultConfigLoadException(String message) {
            super(message);
        }
        public DefaultConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
