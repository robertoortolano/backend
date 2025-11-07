package com.example.demo.repository;

import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectPermissionAssignment;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectPermissionAssignmentRepository extends JpaRepository<ProjectPermissionAssignment, Long> {
    
    /**
     * Trova ProjectPermissionAssignment per tipo, ID permission, progetto e tenant.
     */
    Optional<ProjectPermissionAssignment> findByPermissionTypeAndPermissionIdAndProjectAndTenant(
        String permissionType,
        Long permissionId,
        Project project,
        Tenant tenant
    );
    
    /**
     * Trova ProjectPermissionAssignment con eager fetching di assignment, ruoli e grant.
     */
    @Query("SELECT ppa FROM ProjectPermissionAssignment ppa " +
           "LEFT JOIN FETCH ppa.assignment a " +
           "LEFT JOIN FETCH a.roles " +
           "LEFT JOIN FETCH a.grant g " +
           "LEFT JOIN FETCH g.users " +
           "LEFT JOIN FETCH g.groups " +
           "LEFT JOIN FETCH g.negatedUsers " +
           "LEFT JOIN FETCH g.negatedGroups " +
           "WHERE ppa.permissionType = :permissionType " +
           "AND ppa.permissionId = :permissionId " +
           "AND ppa.project = :project " +
           "AND ppa.tenant = :tenant")
    Optional<ProjectPermissionAssignment> findByPermissionTypeAndPermissionIdAndProjectAndTenantWithCollections(
        @Param("permissionType") String permissionType,
        @Param("permissionId") Long permissionId,
        @Param("project") Project project,
        @Param("tenant") Tenant tenant
    );
    
    /**
     * Trova tutte le ProjectPermissionAssignment per un progetto.
     */
    List<ProjectPermissionAssignment> findByProject(Project project);
    
    /**
     * Trova tutte le ProjectPermissionAssignment per un progetto e tenant.
     */
    List<ProjectPermissionAssignment> findByProjectAndTenant(Project project, Tenant tenant);
    
    /**
     * Trova tutte le ProjectPermissionAssignment per un ItemTypeSet.
     */
    List<ProjectPermissionAssignment> findByItemTypeSetId(Long itemTypeSetId);
    
    /**
     * Trova tutte le ProjectPermissionAssignment per un ItemTypeSet e tenant.
     */
    List<ProjectPermissionAssignment> findByItemTypeSetIdAndTenantId(Long itemTypeSetId, Long tenantId);
    
    /**
     * Elimina ProjectPermissionAssignment per tipo, ID permission, progetto e tenant.
     */
    void deleteByPermissionTypeAndPermissionIdAndProjectAndTenant(
        String permissionType,
        Long permissionId,
        Project project,
        Tenant tenant
    );
    
    /**
     * Elimina tutte le ProjectPermissionAssignment per un ItemTypeSet e tenant.
     */
    void deleteByItemTypeSetIdAndTenantId(Long itemTypeSetId, Long tenantId);
    
    /**
     * Verifica esistenza di ProjectPermissionAssignment.
     */
    boolean existsByPermissionTypeAndPermissionIdAndProjectAndTenant(
        String permissionType,
        Long permissionId,
        Project project,
        Tenant tenant
    );
    
}

