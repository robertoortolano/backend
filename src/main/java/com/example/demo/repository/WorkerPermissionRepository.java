package com.example.demo.repository;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.WorkerPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkerPermissionRepository extends JpaRepository<WorkerPermission, Long> {
    List<WorkerPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration config);
}

