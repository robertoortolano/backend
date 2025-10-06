package com.example.demo.repository;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Trova un ruolo per nome, scope e tenant
    Optional<Role> findByNameAndScopeAndTenant(String name, ScopeType scope, Tenant tenant);

    // Trova tutti i ruoli per scope e tenant
    List<Role> findByScopeAndTenant(ScopeType scope, Tenant tenant);

    // Trova tutti i ruoli di una tenant
    List<Role> findByTenant(Tenant tenant);
    
    // Trova tutti i ruoli per tenant ID
    List<Role> findByTenantId(Long tenantId);

    // Trova tutti i ruoli tenant (scope TENANT) per una tenant
    List<Role> findByScopeAndTenantId(ScopeType scope, Long tenantId);

    // Verifica se un ruolo esiste già nella tenant (utile per validazioni univocità)
    boolean existsByNameAndScopeAndTenant(String name, ScopeType scope, Tenant tenant);

    // Verifica se un ruolo esiste già nella tenant per nome (solo ruoli tenant)
    boolean existsByNameAndTenant(String name, Tenant tenant);

    // Trova tutti i ruoli default per una tenant
    List<Role> findByTenantAndDefaultRoleTrue(Tenant tenant);
}
