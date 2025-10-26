package com.example.demo.repository;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ExecutorPermissionRepository extends JpaRepository<ExecutorPermission, Long> {
    List<ExecutorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<ExecutorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndTransitionId(Long itemTypeConfigurationId, Long transitionId);
    ExecutorPermission findByItemTypeConfigurationAndTransitionId(ItemTypeConfiguration itemTypeConfiguration, Long transitionId);
    List<ExecutorPermission> findByItemTypeConfigurationAndTransitionIdIn(ItemTypeConfiguration itemTypeConfiguration, Set<Long> transitionIds);
    List<ExecutorPermission> findByTransitionId(Long transitionId);
}
