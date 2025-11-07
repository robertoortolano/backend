package com.example.demo.repository;

import com.example.demo.entity.Field;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldStatusPermissionRepository extends JpaRepository<FieldStatusPermission, Long> {
    List<FieldStatusPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    
    /**
     * Trova tutte le FieldStatusPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM FieldStatusPermission p WHERE p.itemTypeConfiguration.id = :itemTypeConfigurationId AND p.itemTypeConfiguration.tenant = :tenant")
    List<FieldStatusPermission> findByItemTypeConfigurationIdAndTenant(@Param("itemTypeConfigurationId") Long itemTypeConfigurationId, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    /**
     * Trova tutte le FieldStatusPermission per una ItemTypeConfiguration
     * RIMOSSO: LEFT JOIN FETCH p.assignedRoles - i ruoli sono ora gestiti tramite PermissionAssignment
     */
    @Query("SELECT p FROM FieldStatusPermission p " +
           "LEFT JOIN FETCH p.workflowStatus ws " +
           "LEFT JOIN FETCH ws.status " +
           "WHERE p.itemTypeConfiguration = :config")
    List<FieldStatusPermission> findAllByItemTypeConfiguration(@Param("config") ItemTypeConfiguration itemTypeConfiguration);
    
    /**
     * Trova tutte le FieldStatusPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     * RIMOSSO: LEFT JOIN FETCH p.assignedRoles - i ruoli sono ora gestiti tramite PermissionAssignment
     */
    @Query("SELECT p FROM FieldStatusPermission p " +
           "LEFT JOIN FETCH p.workflowStatus ws " +
           "LEFT JOIN FETCH ws.status " +
           "WHERE p.itemTypeConfiguration = :config AND p.itemTypeConfiguration.tenant = :tenant")
    List<FieldStatusPermission> findAllByItemTypeConfigurationAndTenant(@Param("config") ItemTypeConfiguration itemTypeConfiguration, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    List<FieldStatusPermission> findAllByItemTypeConfigurationAndPermissionType(ItemTypeConfiguration itemTypeConfiguration, FieldStatusPermission.PermissionType permissionType);
    boolean existsByItemTypeConfigurationIdAndFieldIdAndWorkflowStatusIdAndPermissionType(
            Long itemTypeConfigurationId, 
            Long fieldId, 
            Long workflowStatusId, 
            FieldStatusPermission.PermissionType permissionType
    );
    
    /**
     * Trova FieldStatusPermission per ItemTypeConfiguration, Field, WorkflowStatus e PermissionType
     * RIMOSSO: LEFT JOIN FETCH p.assignedRoles - i ruoli sono ora gestiti tramite PermissionAssignment
     */
    @Query("SELECT p FROM FieldStatusPermission p " +
           "WHERE p.itemTypeConfiguration = :config AND p.field = :field AND " +
           "p.workflowStatus = :workflowStatus AND p.permissionType = :permissionType")
    FieldStatusPermission findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
        @Param("config") ItemTypeConfiguration config,
        @Param("field") Field field,
        @Param("workflowStatus") WorkflowStatus workflowStatus,
        @Param("permissionType") FieldStatusPermission.PermissionType permissionType
    );
}
