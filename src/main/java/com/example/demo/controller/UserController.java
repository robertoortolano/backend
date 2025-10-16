package com.example.demo.controller;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRoleRepository;
import com.example.demo.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRoleRepository userRoleRepository;

    /**
     * Cerca utenti per nome/email nella tenant corrente
     */
    @GetMapping("/search")
    public List<User> searchUsers(
            @RequestParam(required = false, defaultValue = "") String query,
            @CurrentTenant Tenant tenant
    ) {
        if (query == null || query.trim().isEmpty()) {
            // Se non c'è query, ritorna tutti gli utenti della tenant
            return userRoleRepository.findUsersByTenantId(tenant.getId());
        }
        
        return userRoleRepository.searchUsersByTenantId(tenant.getId(), query.trim());
    }
}
