package com.example.demo.repository;

import com.example.demo.entity.ItemTypeSetRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemTypeSetRoleGrantRepository extends JpaRepository<ItemTypeSetRoleGrant, Long> {
    
    List<ItemTypeSetRoleGrant> findByItemTypeSetRoleIdAndTenantId(Long itemTypeSetRoleId, Long tenantId);
    
    List<ItemTypeSetRoleGrant> findByGrantIdAndTenantId(Long grantId, Long tenantId);
    
    @Query("SELECT g FROM ItemTypeSetRoleGrant g WHERE g.itemTypeSetRole.itemTypeSet.id = :itemTypeSetId AND g.tenant.id = :tenantId")
    List<ItemTypeSetRoleGrant> findByItemTypeSetIdAndTenantId(@Param("itemTypeSetId") Long itemTypeSetId, @Param("tenantId") Long tenantId);
    
    @Query("SELECT g FROM ItemTypeSetRoleGrant g WHERE g.itemTypeSetRole.id = :roleId AND g.grant.id = :grantId AND g.tenant.id = :tenantId")
    Optional<ItemTypeSetRoleGrant> findByRoleIdAndGrantIdAndTenantId(@Param("roleId") Long roleId, @Param("grantId") Long grantId, @Param("tenantId") Long tenantId);
    
    boolean existsByItemTypeSetRoleIdAndGrantIdAndTenantId(Long itemTypeSetRoleId, Long grantId, Long tenantId);
    
    void deleteByItemTypeSetRoleIdAndGrantIdAndTenantId(Long itemTypeSetRoleId, Long grantId, Long tenantId);
    
    void deleteByItemTypeSetRoleId(Long itemTypeSetRoleId);
}
