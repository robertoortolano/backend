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

    boolean existsByItemTypeIdAndProjectTenantId(Long itemTypeId, Long tenantId);

    ItemTypeConfiguration findByIdAndTenant(Long itemTypeId, Tenant tenant);

    boolean existsByFieldSetIdAndFieldSetTenantId(Long fieldSetId, Long tenantId);

    boolean existsByWorkflowIdAndWorkflowTenantId(Long workflowId, Long tenantId);


}
