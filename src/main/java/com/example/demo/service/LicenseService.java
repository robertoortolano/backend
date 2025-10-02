package com.example.demo.service;

import com.example.demo.repository.LicenseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class LicenseService {

    // per ora simuliamo un set di chiavi di licenza valide
    private static final Set<String> VALID_LICENSE_KEYS = new HashSet<>();

    private final LicenseRepository licenseRepository;

    static {
        VALID_LICENSE_KEYS.add("LICENSE-1234-ABCD");
        VALID_LICENSE_KEYS.add("LICENSE-5678-EFGH");
        VALID_LICENSE_KEYS.add("LICENSE-9012-IJKL");
    }

    /**
     * Controlla se la licenseKey fornita è valida.
     * @param licenseKey la chiave di licenza
     * @return true se valida, false altrimenti
     */
    public boolean isValidLicenseKey(String licenseKey) {
        return licenseKey != null && VALID_LICENSE_KEYS.contains(licenseKey);
    }

    public boolean exists(String licenseKey) {
        return licenseRepository.existsByLicenseKey(licenseKey);
    }

}
