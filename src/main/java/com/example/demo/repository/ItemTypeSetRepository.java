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
        WHERE its.tenant = :tenant AND its.scope = 'GLOBAL'
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
    WHERE s.id = :id AND s.tenant = :tenant
""")
    Optional<ItemTypeSet> findByIdWithItemTypeConfigurationsAndTenant(@Param("id") Long id, @Param("tenant") Tenant tenant);

    boolean existsByItemTypeConfigurations_IdAndTenant_Id(Long itemTypeConfigurationId, Long tenantId);

    void deleteByIdAndTenant(Long id, Tenant tenant);

}
