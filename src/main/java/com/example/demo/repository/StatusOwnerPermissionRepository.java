package com.example.demo.repository;

import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface StatusOwnerPermissionRepository extends JpaRepository<StatusOwnerPermission, Long> {
    List<StatusOwnerPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    
    /**
     * Trova tutte le StatusOwnerPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM StatusOwnerPermission p WHERE p.itemTypeConfiguration.id = :itemTypeConfigurationId AND p.itemTypeConfiguration.tenant = :tenant")
    List<StatusOwnerPermission> findByItemTypeConfigurationIdAndTenant(@Param("itemTypeConfigurationId") Long itemTypeConfigurationId, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    /**
     * Trova tutte le StatusOwnerPermission per una ItemTypeConfiguration
     * RIMOSSO: LEFT JOIN FETCH p.assignedRoles - i ruoli sono ora gestiti tramite PermissionAssignment
     */
    @Query("SELECT p FROM StatusOwnerPermission p " +
           "LEFT JOIN FETCH p.workflowStatus ws " +
           "LEFT JOIN FETCH ws.status " +
           "WHERE p.itemTypeConfiguration = :config")
    List<StatusOwnerPermission> findAllByItemTypeConfiguration(@Param("config") ItemTypeConfiguration itemTypeConfiguration);
    
    /**
     * Trova tutte le StatusOwnerPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     * RIMOSSO: LEFT JOIN FETCH p.assignedRoles - i ruoli sono ora gestiti tramite PermissionAssignment
     */
    @Query("SELECT p FROM StatusOwnerPermission p " +
           "LEFT JOIN FETCH p.workflowStatus ws " +
           "LEFT JOIN FETCH ws.status " +
           "WHERE p.itemTypeConfiguration = :config AND p.itemTypeConfiguration.tenant = :tenant")
    List<StatusOwnerPermission> findAllByItemTypeConfigurationAndTenant(@Param("config") ItemTypeConfiguration itemTypeConfiguration, @Param("tenant") com.example.demo.entity.Tenant tenant);

    @Query("SELECT p FROM StatusOwnerPermission p WHERE p.itemTypeConfiguration.id IN :itemTypeConfigurationIds AND p.itemTypeConfiguration.tenant = :tenant")
    List<StatusOwnerPermission> findAllByItemTypeConfigurationIdInAndTenant(@Param("itemTypeConfigurationIds") Collection<Long> itemTypeConfigurationIds,
                                                                            @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    boolean existsByItemTypeConfigurationIdAndWorkflowStatusId(Long itemTypeConfigurationId, Long workflowStatusId);
    StatusOwnerPermission findByItemTypeConfigurationAndWorkflowStatusId(ItemTypeConfiguration itemTypeConfiguration, Long workflowStatusId);
    List<StatusOwnerPermission> findByItemTypeConfigurationAndWorkflowStatusIdIn(ItemTypeConfiguration itemTypeConfiguration, Set<Long> workflowStatusIds);
    List<StatusOwnerPermission> findByWorkflowStatusId(Long workflowStatusId);
    
    /**
     * Trova StatusOwnerPermission per WorkflowStatus ID, filtrato per Tenant (sicurezza)
     */
    @Query("SELECT p FROM StatusOwnerPermission p JOIN p.workflowStatus ws JOIN ws.workflow w WHERE ws.id = :workflowStatusId AND w.tenant = :tenant")
    List<StatusOwnerPermission> findByWorkflowStatusIdAndTenant(@Param("workflowStatusId") Long workflowStatusId, @Param("tenant") com.example.demo.entity.Tenant tenant);
}
