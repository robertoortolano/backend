package com.example.demo.repository;

import com.example.demo.entity.CreatorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreatorPermissionRepository extends JpaRepository<CreatorPermission, Long> {
    List<CreatorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    
    /**
     * Trova tutte le CreatorPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM CreatorPermission p WHERE p.itemTypeConfiguration.id = :itemTypeConfigurationId AND p.itemTypeConfiguration.tenant = :tenant")
    List<CreatorPermission> findByItemTypeConfigurationIdAndTenant(@Param("itemTypeConfigurationId") Long itemTypeConfigurationId, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    List<CreatorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    
    /**
     * Trova tutte le CreatorPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM CreatorPermission p WHERE p.itemTypeConfiguration = :config AND p.itemTypeConfiguration.tenant = :tenant")
    List<CreatorPermission> findAllByItemTypeConfigurationAndTenant(@Param("config") ItemTypeConfiguration itemTypeConfiguration, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    boolean existsByItemTypeConfigurationId(Long itemTypeConfigurationId);
}
