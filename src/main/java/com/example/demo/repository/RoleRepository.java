package com.example.demo.repository;

import com.example.demo.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository per Role (ruoli custom per Permission system).
 * Nota: I ruoli di sistema ADMIN/USER sono gestiti da UserRoleRepository.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Trova un ruolo per nome e tenant
    Optional<Role> findByNameAndTenant(String name, Tenant tenant);

    // Trova tutti i ruoli di una tenant
    List<Role> findByTenant(Tenant tenant);
    
    // Trova tutti i ruoli per tenant ID
    List<Role> findByTenantId(Long tenantId);

    // Verifica se un ruolo esiste già nella tenant per nome
    boolean existsByNameAndTenant(String name, Tenant tenant);

    // Trova tutti i ruoli default per una tenant
    List<Role> findByTenantAndDefaultRoleTrue(Tenant tenant);
    
    // Trova un ruolo per ID e tenant (sicurezza)
    Optional<Role> findByIdAndTenant(Long id, Tenant tenant);
}
