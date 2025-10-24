// FieldSetRepository.java
package com.example.demo.repository;

import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import com.example.demo.enums.ScopeType;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldSetRepository extends JpaRepository<FieldSet, Long> {

    FieldSet findFirstByTenantAndDefaultFieldSetTrue(Tenant tenant);

    @EntityGraph(attributePaths = {
            "fieldSetEntries",
            "fieldSetEntries.fieldConfiguration",
            "fieldSetEntries.fieldConfiguration.field"
    })
    List<FieldSet> findByTenantAndScope(Tenant tenant, ScopeType scope);

    @EntityGraph(attributePaths = {
            "fieldSetEntries",
            "fieldSetEntries.fieldConfiguration",
            "fieldSetEntries.fieldConfiguration.field"
    })
    List<FieldSet> findByTenantAndProjectIdAndScope(Tenant tenant, Long projectId, ScopeType scope);

    @Query("""
    SELECT DISTINCT fse.fieldSet
    FROM FieldSetEntry fse
    WHERE fse.fieldConfiguration IN :configs
""")
    List<FieldSet> findByFieldConfigurationsAndTenant(@Param("configs") List<FieldConfiguration> configs);

    Optional<FieldSet> findByIdAndTenant(Long id, Tenant tenant);

    void deleteByIdAndTenant(Long id, Tenant tenant);

}
