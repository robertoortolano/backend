package com.example.demo.repository;

import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface StatusOwnerPermissionRepository extends JpaRepository<StatusOwnerPermission, Long> {
    List<StatusOwnerPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    
    /**
     * Trova tutte le StatusOwnerPermission per una ItemTypeConfiguration
     * IMPORTANTE: Carica anche i ruoli associati (JOIN FETCH) per evitare problemi di lazy loading
     */
    @Query("SELECT p FROM StatusOwnerPermission p " +
           "LEFT JOIN FETCH p.assignedRoles " +
           "LEFT JOIN FETCH p.workflowStatus ws " +
           "LEFT JOIN FETCH ws.status " +
           "WHERE p.itemTypeConfiguration = :config")
    List<StatusOwnerPermission> findAllByItemTypeConfiguration(@Param("config") ItemTypeConfiguration itemTypeConfiguration);
    
    boolean existsByItemTypeConfigurationIdAndWorkflowStatusId(Long itemTypeConfigurationId, Long workflowStatusId);
    StatusOwnerPermission findByItemTypeConfigurationAndWorkflowStatusId(ItemTypeConfiguration itemTypeConfiguration, Long workflowStatusId);
    List<StatusOwnerPermission> findByItemTypeConfigurationAndWorkflowStatusIdIn(ItemTypeConfiguration itemTypeConfiguration, Set<Long> workflowStatusIds);
    List<StatusOwnerPermission> findByWorkflowStatusId(Long workflowStatusId);
}
