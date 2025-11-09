package com.example.demo.repository;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.WorkerPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkerPermissionRepository extends JpaRepository<WorkerPermission, Long> {
    List<WorkerPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration config);
    
    /**
     * Trova tutte le WorkerPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM WorkerPermission p WHERE p.itemTypeConfiguration = :config AND p.itemTypeConfiguration.tenant = :tenant")
    List<WorkerPermission> findAllByItemTypeConfigurationAndTenant(@Param("config") ItemTypeConfiguration config, @Param("tenant") com.example.demo.entity.Tenant tenant);

    @Query("SELECT p FROM WorkerPermission p WHERE p.itemTypeConfiguration.id IN :itemTypeConfigurationIds AND p.itemTypeConfiguration.tenant = :tenant")
    List<WorkerPermission> findAllByItemTypeConfigurationIdInAndTenant(@Param("itemTypeConfigurationIds") Collection<Long> itemTypeConfigurationIds,
                                                                       @Param("tenant") com.example.demo.entity.Tenant tenant);
}





