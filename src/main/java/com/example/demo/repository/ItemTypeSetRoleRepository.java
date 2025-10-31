package com.example.demo.repository;

import com.example.demo.entity.ItemTypeSetRole;
import com.example.demo.enums.ItemTypeSetRoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemTypeSetRoleRepository extends JpaRepository<ItemTypeSetRole, Long> {
    
    List<ItemTypeSetRole> findByItemTypeSetIdAndTenantId(Long itemTypeSetId, Long tenantId);
    
    List<ItemTypeSetRole> findByItemTypeSetIdAndRoleTypeAndTenantId(Long itemTypeSetId, ItemTypeSetRoleType roleType, Long tenantId);
    
    Optional<ItemTypeSetRole> findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
            Long itemTypeSetId, String relatedEntityType, Long relatedEntityId, ItemTypeSetRoleType roleType, Long tenantId);
    
    @Query("SELECT r FROM ItemTypeSetRole r WHERE r.itemTypeSet.id = :itemTypeSetId AND r.roleType = :roleType AND r.tenant.id = :tenantId")
    List<ItemTypeSetRole> findRolesByItemTypeSetAndType(@Param("itemTypeSetId") Long itemTypeSetId, 
                                                        @Param("roleType") ItemTypeSetRoleType roleType, 
                                                        @Param("tenantId") Long tenantId);
    
    @Query("SELECT r FROM ItemTypeSetRole r WHERE r.itemTypeSet.id = :itemTypeSetId AND r.relatedEntityType = :entityType AND r.tenant.id = :tenantId")
    List<ItemTypeSetRole> findRolesByItemTypeSetAndEntityType(@Param("itemTypeSetId") Long itemTypeSetId, 
                                                              @Param("entityType") String entityType, 
                                                              @Param("tenantId") Long tenantId);
    
    boolean existsByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
            Long itemTypeSetId, String relatedEntityType, Long relatedEntityId, ItemTypeSetRoleType roleType, Long tenantId);
    
    void deleteByItemTypeSetIdAndTenantId(Long itemTypeSetId, Long tenantId);
    
    // Trova un ruolo per ID e tenant (sicurezza)
    Optional<ItemTypeSetRole> findByIdAndTenantId(Long id, Long tenantId);
    
    // Trova un ruolo per ID e tenant (usando oggetto Tenant)
    @Query("SELECT r FROM ItemTypeSetRole r WHERE r.id = :id AND r.tenant = :tenant")
    Optional<ItemTypeSetRole> findByIdAndTenant(@Param("id") Long id, @Param("tenant") com.example.demo.entity.Tenant tenant);
}
