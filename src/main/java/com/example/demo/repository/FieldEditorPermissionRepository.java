package com.example.demo.repository;

import com.example.demo.entity.FieldEditorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldEditorPermissionRepository extends JpaRepository<FieldEditorPermission, Long> {
    List<FieldEditorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<FieldEditorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndFieldConfigurationId(Long itemTypeConfigurationId, Long fieldConfigurationId);
}
