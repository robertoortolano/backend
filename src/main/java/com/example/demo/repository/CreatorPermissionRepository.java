package com.example.demo.repository;

import com.example.demo.entity.CreatorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreatorPermissionRepository extends JpaRepository<CreatorPermission, Long> {
    List<CreatorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<CreatorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationId(Long itemTypeConfigurationId);
}
