package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.Permission;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(-1) // Eseguito per primo, prima degli altri initializer
@RequiredArgsConstructor
public class PermissionInitializer implements TenantInitializer {

    private final PermissionRepository permissionRepository;

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        // I ruoli di ItemTypeSet sono globali, non legati a un tenant specifico
        // Vengono creati una sola volta per tutto il sistema
        if (config.getPermissions() != null) {
            for (DefaultConfig.Permission permissionDto : config.getPermissions()) {
                // Controlla se la permission esiste già
                if (!permissionRepository.existsByName(permissionDto.getName())) {
                    Permission permission = new Permission();
                    permission.setName(permissionDto.getName());
                    permission.setDescription(permissionDto.getDescription());
                    permission.setSystemPermission(true);
                    
                    permissionRepository.save(permission);
                }
            }
        }
    }
}
