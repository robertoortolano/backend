package com.example.demo.repository;

import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
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
     * Trova PermissionAssignment globale per tipo, ID permission e tenant (project = null).
     */
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(
        String permissionType, 
        Long permissionId, 
        Tenant tenant
    );
    
    /**
     * Trova PermissionAssignment per tipo, ID permission, tenant e progetto.
     */
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenantAndProject(
        String permissionType, 
        Long permissionId, 
        Tenant tenant,
        Project project
    );
    
    /**
     * Trova PermissionAssignment globale con eager fetching di ruoli e grant (project = null).
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
           "AND pa.tenant = :tenant " +
           "AND pa.project IS NULL")
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenantWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionId") Long permissionId,
        @Param("tenant") Tenant tenant
    );
    
    /**
     * Trova PermissionAssignment di progetto con eager fetching di ruoli e grant.
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
           "AND pa.tenant = :tenant " +
           "AND pa.project = :project")
    Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenantAndProjectWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionId") Long permissionId,
        @Param("tenant") Tenant tenant,
        @Param("project") Project project
    );
    
    @Query("SELECT pa FROM PermissionAssignment pa " +
           "LEFT JOIN FETCH pa.roles " +
           "LEFT JOIN FETCH pa.grant g " +
           "LEFT JOIN FETCH g.users " +
           "LEFT JOIN FETCH g.groups " +
           "LEFT JOIN FETCH g.negatedUsers " +
           "LEFT JOIN FETCH g.negatedGroups " +
           "WHERE pa.permissionType = :permissionType " +
           "AND pa.permissionId IN :permissionIds " +
           "AND pa.tenant = :tenant " +
           "AND pa.project IS NULL")
    List<PermissionAssignment> findAllByPermissionTypeAndPermissionIdInAndTenantWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionIds") List<Long> permissionIds,
        @Param("tenant") Tenant tenant
    );
    
    @Query("SELECT pa FROM PermissionAssignment pa " +
           "LEFT JOIN FETCH pa.roles " +
           "LEFT JOIN FETCH pa.grant g " +
           "LEFT JOIN FETCH g.users " +
           "LEFT JOIN FETCH g.groups " +
           "LEFT JOIN FETCH g.negatedUsers " +
           "LEFT JOIN FETCH g.negatedGroups " +
           "WHERE pa.permissionType = :permissionType " +
           "AND pa.permissionId IN :permissionIds " +
           "AND pa.tenant = :tenant " +
           "AND pa.project = :project")
    List<PermissionAssignment> findAllByPermissionTypeAndPermissionIdInAndTenantAndProjectWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionIds") List<Long> permissionIds,
        @Param("tenant") Tenant tenant,
        @Param("project") Project project
    );
    
    /**
     * Trova tutte le PermissionAssignment per un tenant.
     */
    List<PermissionAssignment> findByTenant(Tenant tenant);
    
    /**
     * Trova tutte le PermissionAssignment globali per un tenant (project = null).
     */
    List<PermissionAssignment> findByTenantAndProjectIsNull(Tenant tenant);
    
    /**
     * Trova tutte le PermissionAssignment per tipo di permission e tenant.
     */
    List<PermissionAssignment> findByPermissionTypeAndTenant(String permissionType, Tenant tenant);
    
    /**
     * Trova tutte le PermissionAssignment globali per tipo di permission e tenant (project = null).
     */
    List<PermissionAssignment> findByPermissionTypeAndTenantAndProjectIsNull(String permissionType, Tenant tenant);
    
    /**
     * Trova tutte le PermissionAssignment per un progetto.
     */
    List<PermissionAssignment> findByProject(Project project);
    
    /**
     * Trova tutte le PermissionAssignment per un ItemTypeSet (project != null).
     */
    @Query("SELECT pa FROM PermissionAssignment pa " +
           "WHERE pa.itemTypeSet.id = :itemTypeSetId " +
           "AND pa.project IS NOT NULL")
    List<PermissionAssignment> findByItemTypeSetIdAndProjectIsNotNull(@Param("itemTypeSetId") Long itemTypeSetId);
    
    /**
     * Elimina PermissionAssignment globale per tipo, ID permission e tenant (project = null).
     */
    void deleteByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(String permissionType, Long permissionId, Tenant tenant);
    
    /**
     * Elimina PermissionAssignment per tipo, ID permission, tenant e progetto.
     */
    void deleteByPermissionTypeAndPermissionIdAndTenantAndProject(String permissionType, Long permissionId, Tenant tenant, Project project);
    
    /**
     * Verifica esistenza di PermissionAssignment globale (project = null).
     */
    boolean existsByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(String permissionType, Long permissionId, Tenant tenant);
    
    /**
     * Verifica esistenza di PermissionAssignment per progetto.
     */
    boolean existsByPermissionTypeAndPermissionIdAndTenantAndProject(String permissionType, Long permissionId, Tenant tenant, Project project);
    
    /**
     * @deprecated Usa findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull per assegnazioni globali
     */
    @Deprecated
    default Optional<PermissionAssignment> findByPermissionTypeAndPermissionIdAndTenant(
        String permissionType, 
        Long permissionId, 
        Tenant tenant
    ) {
        return findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant);
    }
    
    /**
     * @deprecated Usa findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull per assegnazioni globali
     */
    @Deprecated
    default boolean existsByPermissionTypeAndPermissionIdAndTenant(String permissionType, Long permissionId, Tenant tenant) {
        return existsByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant);
    }
    
    /**
     * @deprecated Usa deleteByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull per assegnazioni globali
     */
    @Deprecated
    default void deleteByPermissionTypeAndPermissionIdAndTenant(String permissionType, Long permissionId, Tenant tenant) {
        deleteByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant);
    }
    
}

