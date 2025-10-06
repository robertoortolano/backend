package com.example.demo.repository;

import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldStatusPermissionRepository extends JpaRepository<FieldStatusPermission, Long> {
    List<FieldStatusPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<FieldStatusPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    List<FieldStatusPermission> findAllByItemTypeConfigurationAndPermissionType(ItemTypeConfiguration itemTypeConfiguration, FieldStatusPermission.PermissionType permissionType);
    boolean existsByItemTypeConfigurationIdAndFieldConfigurationIdAndWorkflowStatusIdAndPermissionType(
            Long itemTypeConfigurationId, 
            Long fieldConfigurationId, 
            Long workflowStatusId, 
            FieldStatusPermission.PermissionType permissionType
    );
}
