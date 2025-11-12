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

    /**
     * Trova tutti i FieldSet che usano le FieldConfiguration specificate
     * NOTA: Questo metodo non filtra per tenant - usare findByFieldConfigurationsAndTenant per sicurezza
     */
    @Query("""
    SELECT DISTINCT fse.fieldSet
    FROM FieldSetEntry fse
    WHERE fse.fieldConfiguration IN :configs
""")
    List<FieldSet> findByFieldConfigurations(@Param("configs") List<FieldConfiguration> configs);

    /**
     * Trova tutti i FieldSet che usano le FieldConfiguration specificate, filtrati per Tenant (sicurezza)
     */
    @Query("""
    SELECT DISTINCT fse.fieldSet
    FROM FieldSetEntry fse
    WHERE fse.fieldConfiguration IN :configs AND fse.fieldSet.tenant = :tenant
""")
    List<FieldSet> findByFieldConfigurationsAndTenant(@Param("configs") List<FieldConfiguration> configs, @Param("tenant") Tenant tenant);

    Optional<FieldSet> findByIdAndTenant(Long id, Tenant tenant);

    /**
     * Trova un FieldSet per ID e Tenant, caricando anche i fieldSetEntries con le relative fieldConfiguration e field.
     * Questo metodo è utile quando è necessario accedere ai fieldSetEntries senza problemi di lazy loading.
     */
    @EntityGraph(attributePaths = {
            "fieldSetEntries",
            "fieldSetEntries.fieldConfiguration",
            "fieldSetEntries.fieldConfiguration.field"
    })
    @Query("SELECT fs FROM FieldSet fs WHERE fs.id = :id AND fs.tenant = :tenant")
    Optional<FieldSet> findByIdAndTenantWithEntries(@Param("id") Long id, @Param("tenant") Tenant tenant);

    void deleteByIdAndTenant(Long id, Tenant tenant);

}
