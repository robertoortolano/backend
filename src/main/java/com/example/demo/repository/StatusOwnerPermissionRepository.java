package com.example.demo.repository;

import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusOwnerPermissionRepository extends JpaRepository<StatusOwnerPermission, Long> {
    List<StatusOwnerPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<StatusOwnerPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndWorkflowStatusId(Long itemTypeConfigurationId, Long workflowStatusId);
}
