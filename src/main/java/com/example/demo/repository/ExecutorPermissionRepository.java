package com.example.demo.repository;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutorPermissionRepository extends JpaRepository<ExecutorPermission, Long> {
    List<ExecutorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<ExecutorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndTransitionId(Long itemTypeConfigurationId, Long transitionId);
}
