package com.example.demo.service;

import com.example.demo.entity.Permission;
import com.example.demo.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /**
     * Ottiene tutte le permissions di sistema (i 7 ruoli predefiniti)
     */
    public List<Permission> getAllSystemPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * Ottiene una permission per nome
     */
    public Permission getPermissionByName(String name) {
        return permissionRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Permission non trovata: " + name));
    }

    /**
     * Verifica se una permission esiste
     */
    public boolean existsByName(String name) {
        return permissionRepository.existsByName(name);
    }

    /**
     * Ottiene le 7 permissions predefinite per ItemTypeSet
     */
    public List<Permission> getItemTypeSetPermissions() {
        return permissionRepository.findAll();
    }
}

