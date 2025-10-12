package com.example.demo.repository;

import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldOwnerPermissionRepository extends JpaRepository<FieldOwnerPermission, Long> {
    List<FieldOwnerPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<FieldOwnerPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndFieldConfigurationId(Long itemTypeConfigurationId, Long fieldConfigurationId);
}
