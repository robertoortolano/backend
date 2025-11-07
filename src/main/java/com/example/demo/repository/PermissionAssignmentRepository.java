package com.example.demo.repository;

import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionAssignmentRepository extends JpaRepository<PermissionAssignment, Long> {
    
    /**
     * Trova PermissionAssignment per tipo, ID permission e tenant.
     */
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenant(
        String permissionType, 
        Long permissionId, 
        Tenant tenant
    );
    
    /**
     * Trova PermissionAssignment con eager fetching di ruoli e grant.
     */
    @Query("SELECT pa FROM PermissionAssignment pa " +
           "LEFT JOIN FETCH pa.roles " +
           "LEFT JOIN FETCH pa.grant g " +
           "LEFT JOIN FETCH g.users " +
           "LEFT JOIN FETCH g.groups " +
           "LEFT JOIN FETCH g.negatedUsers " +
           "LEFT JOIN FETCH g.negatedGroups " +
           "WHERE pa.permissionType = :permissionType " +
           "AND pa.permissionId = :permissionId " +
           "AND pa.tenant = :tenant")
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenantWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionId") Long permissionId,
        @Param("tenant") Tenant tenant
    );
    
    /**
     * Trova tutte le PermissionAssignment per un tenant.
     */
    List<PermissionAssignment> findByTenant(Tenant tenant);
    
    /**
     * Trova tutte le PermissionAssignment per tipo di permission e tenant.
     */
    List<PermissionAssignment> findByPermissionTypeAndTenant(String permissionType, Tenant tenant);
    
    /**
     * Elimina PermissionAssignment per tipo, ID permission e tenant.
     */
    void deleteByPermissionTypeAndPermissionIdAndTenant(String permissionType, Long permissionId, Tenant tenant);
    
    /**
     * Verifica esistenza di PermissionAssignment.
     */
    boolean existsByPermissionTypeAndPermissionIdAndTenant(String permissionType, Long permissionId, Tenant tenant);
    
}

