package com.example.demo.repository;

import com.example.demo.entity.FieldConfiguration;
import com.example.demo.enums.ScopeType;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldConfigurationRepository extends JpaRepository<FieldConfiguration, Long> {

    List<FieldConfiguration> findByTenantAndScope(Tenant tenant, ScopeType scopeType);
    List<FieldConfiguration> findByFieldIdAndTenant(Long fieldId, Tenant tenant);
    boolean existsByFieldIdAndTenant(Long fieldId, Tenant tenant);

    List<FieldConfiguration> findByFieldIdAndProjectIsNotNullAndTenant(Long fieldId, Tenant tenant);

    Optional<FieldConfiguration> findByIdAndTenant(Long id, Tenant tenant);

    List<FieldConfiguration> findAllByIdInAndTenant(List<Long> configIds, Tenant tenant);

    void deleteByIdAndTenant(Long id, Tenant tenant);
}

