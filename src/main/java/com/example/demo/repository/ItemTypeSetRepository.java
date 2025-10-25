package com.example.demo.repository;

import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemTypeSetRepository extends JpaRepository<ItemTypeSet, Long> {
    // Puoi aggiungere metodi custom se servono, per esempio:
    @Query("""
        SELECT DISTINCT s FROM ItemTypeSet s
        LEFT JOIN FETCH s.itemTypeConfigurations e
        LEFT JOIN FETCH e.itemType
        WHERE s.tenant.id = :tenantId
    """)
    List<ItemTypeSet> findAllByTenantIdWithItemTypeConfigurations(@Param("tenantId") Long tenantId);



    ItemTypeSet findFirstByTenantAndDefaultItemTypeSetTrue(Tenant tenant);


    @Query("""
        SELECT DISTINCT its FROM ItemTypeSet its
        LEFT JOIN FETCH its.itemTypeConfigurations
        WHERE its.tenant = :tenant AND its.scope = 'TENANT'
    """)
    List<ItemTypeSet> findAllGlobalWithItemTypeConfigurationsByTenant(@Param("tenant") Tenant tenant);

    @Query("""
    SELECT DISTINCT its FROM ItemTypeSet its
    LEFT JOIN FETCH its.itemTypeConfigurations
    WHERE its.tenant = :tenant AND its.scope = 'GLOBAL'
""")
    List<ItemTypeSet> findAllNonGlobalWithItemTypeConfigurationsByTenant(@Param("tenant") Tenant tenant);


    Optional<ItemTypeSet> findByIdAndTenant(Long id, Tenant tenant);



    @Query("""
    SELECT s FROM ItemTypeSet s
    LEFT JOIN FETCH s.itemTypeConfigurations e
    LEFT JOIN FETCH e.itemType
    LEFT JOIN FETCH e.workflow w
    LEFT JOIN FETCH e.fieldSet fs
    WHERE s.id = :id AND s.tenant = :tenant
""")
    Optional<ItemTypeSet> findByIdWithItemTypeConfigurationsAndTenant(@Param("id") Long id, @Param("tenant") Tenant tenant);

    boolean existsByItemTypeConfigurations_IdAndTenant_Id(Long itemTypeConfigurationId, Long tenantId);

    /**
     * Trova tutti gli ItemTypeSet che usano un FieldSet specifico
     */
    @Query("""
        SELECT DISTINCT its FROM ItemTypeSet its
        JOIN its.itemTypeConfigurations itc
        WHERE itc.fieldSet.id = :fieldSetId 
        AND its.tenant = :tenant
    """)
    List<ItemTypeSet> findByItemTypeConfigurationsFieldSetIdAndTenant(
        @Param("fieldSetId") Long fieldSetId, 
        @Param("tenant") Tenant tenant
    );

    /**
     * Trova tutti gli ItemTypeSet che usano un Workflow specifico
     */
    @Query("""
        SELECT DISTINCT its FROM ItemTypeSet its
        JOIN its.itemTypeConfigurations itc
        WHERE itc.workflow.id = :workflowId 
        AND its.tenant = :tenant
    """)
    List<ItemTypeSet> findByItemTypeConfigurationsWorkflowIdAndTenant(
        @Param("workflowId") Long workflowId, 
        @Param("tenant") Tenant tenant
    );

    void deleteByIdAndTenant(Long id, Tenant tenant);

    @Query("""
    SELECT DISTINCT s FROM ItemTypeSet s
    LEFT JOIN FETCH s.itemTypeConfigurations e
    LEFT JOIN FETCH e.itemType
    LEFT JOIN FETCH e.workflow w
    LEFT JOIN FETCH w.statuses ws
    LEFT JOIN FETCH ws.status
    LEFT JOIN FETCH w.transitions t
    LEFT JOIN FETCH t.fromStatus fs
    LEFT JOIN FETCH fs.status
    LEFT JOIN FETCH t.toStatus ts
    LEFT JOIN FETCH ts.status
    LEFT JOIN FETCH e.fieldSet fs2
    WHERE s.id = :id AND s.tenant = :tenant
""")
    Optional<ItemTypeSet> findByIdWithAllRelations(@Param("id") Long id, @Param("tenant") Tenant tenant);

}
