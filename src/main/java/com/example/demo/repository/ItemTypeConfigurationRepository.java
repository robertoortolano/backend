package com.example.demo.repository;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ItemTypeConfigurationRepository extends JpaRepository<ItemTypeConfiguration, Long> {

    // Trova tutte le configurazioni per una tenant
    List<ItemTypeConfiguration> findByTenant(Tenant tenant);

    // Trova tutte le configurazioni globali (scope = GLOBAL) per una tenant
    List<ItemTypeConfiguration> findByTenantAndScope(Tenant tenant, ScopeType scope);

    // Trova tutte le configurazioni per categoria e tenant
    List<ItemTypeConfiguration> findByTenantAndCategory(Tenant tenant, ItemTypeCategory category);

    // Trova configurazioni di default in una tenant
    List<ItemTypeConfiguration> findByTenantAndDefaultItemTypeConfigurationTrue(Tenant tenant);

    @Query("""
    SELECT itc.project
    FROM ItemTypeConfiguration itc
    WHERE itc.itemType.id = :itemTypeId
      AND itc.project IS NOT NULL
      AND itc.project.tenant.id = :tenantId
""")
    Set<Project> findProjectsUsingItemType(
            @Param("itemTypeId") Long itemTypeId,
            @Param("tenantId") Long tenantId
    );

    @Query("SELECT COUNT(itc) > 0 FROM ItemTypeConfiguration itc WHERE itc.itemType.id = :itemTypeId AND itc.tenant.id = :tenantId")
    boolean existsByItemTypeIdAndProjectTenantId(@Param("itemTypeId") Long itemTypeId, @Param("tenantId") Long tenantId);

    ItemTypeConfiguration findByIdAndTenant(Long itemTypeId, Tenant tenant);

    @Query("SELECT COUNT(itc) > 0 FROM ItemTypeConfiguration itc WHERE itc.fieldSet.id = :fieldSetId AND itc.tenant.id = :tenantId")
    boolean existsByFieldSetIdAndFieldSetTenantId(@Param("fieldSetId") Long fieldSetId, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(itc) > 0 FROM ItemTypeConfiguration itc WHERE itc.workflow.id = :workflowId AND itc.tenant.id = :tenantId")
    boolean existsByWorkflowIdAndWorkflowTenantId(@Param("workflowId") Long workflowId, @Param("tenantId") Long tenantId);

    List<ItemTypeConfiguration> findByItemTypeIdAndTenant(Long itemTypeId, Tenant tenant);

    @Query("SELECT itc FROM ItemTypeConfiguration itc WHERE itc.workflow.id = :workflowId AND itc.tenant.id = :tenantId")
    List<ItemTypeConfiguration> findByWorkflowIdAndTenant(@Param("workflowId") Long workflowId, @Param("tenantId") Long tenantId);

    /**
     * Trova un ItemTypeConfiguration per ID, caricando anche workflow e fieldset con JOIN FETCH.
     * Questo metodo è utile quando è necessario accedere a workflow e fieldset senza problemi di lazy loading.
     */
    @Query("""
        SELECT itc FROM ItemTypeConfiguration itc
        LEFT JOIN FETCH itc.workflow
        LEFT JOIN FETCH itc.fieldSet
        WHERE itc.id = :id
    """)
    java.util.Optional<ItemTypeConfiguration> findByIdWithWorkflowAndFieldSet(@Param("id") Long id);

}
