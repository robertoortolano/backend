package com.example.demo.repository;

import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Trova un ruolo per nome, scope e tenant
    Optional<Role> findByNameAndScopeAndTenant(RoleName name, ScopeType scope, Tenant tenant);

    // Trova tutti i ruoli globali (es: scope GLOBAL)
    List<Role> findByScopeAndTenant(ScopeType scope, Tenant tenant);

    // Trova tutti i ruoli di una tenant
    List<Role> findByTenant(Tenant tenant);

    // Trova un ruolo di default per nome e tenant
    Optional<Role> findByNameAndTenantAndDefaultRoleTrue(RoleName name, Tenant tenant);

    // Verifica se un ruolo esiste già nella tenant (utile per validazioni univocità)
    boolean existsByNameAndScopeAndTenant(RoleName name, ScopeType scope, Tenant tenant);

    // Trova tutti i ruoli default per una tenant
    List<Role> findByTenantAndDefaultRoleTrue(Tenant tenant);
}
