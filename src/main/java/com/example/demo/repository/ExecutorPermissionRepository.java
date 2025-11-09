package com.example.demo.repository;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ExecutorPermissionRepository extends JpaRepository<ExecutorPermission, Long> {
    List<ExecutorPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    
    /**
     * Trova tutte le ExecutorPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM ExecutorPermission p WHERE p.itemTypeConfiguration.id = :itemTypeConfigurationId AND p.itemTypeConfiguration.tenant = :tenant")
    List<ExecutorPermission> findByItemTypeConfigurationIdAndTenant(@Param("itemTypeConfigurationId") Long itemTypeConfigurationId, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    List<ExecutorPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    
    /**
     * Trova tutte le ExecutorPermission per una ItemTypeConfiguration, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT p FROM ExecutorPermission p WHERE p.itemTypeConfiguration = :config AND p.itemTypeConfiguration.tenant = :tenant")
    List<ExecutorPermission> findAllByItemTypeConfigurationAndTenant(@Param("config") ItemTypeConfiguration itemTypeConfiguration, @Param("tenant") com.example.demo.entity.Tenant tenant);
    
    boolean existsByItemTypeConfigurationIdAndTransitionId(Long itemTypeConfigurationId, Long transitionId);
    ExecutorPermission findByItemTypeConfigurationAndTransitionId(ItemTypeConfiguration itemTypeConfiguration, Long transitionId);
    List<ExecutorPermission> findByItemTypeConfigurationAndTransitionIdIn(ItemTypeConfiguration itemTypeConfiguration, Set<Long> transitionIds);
    List<ExecutorPermission> findByTransitionId(Long transitionId);
    
    /**
     * Trova ExecutorPermission per Transition ID, filtrato per Tenant (sicurezza)
     */
    @Query("SELECT p FROM ExecutorPermission p JOIN p.transition t JOIN t.workflow w WHERE t.id = :transitionId AND w.tenant = :tenant")
    List<ExecutorPermission> findByTransitionIdAndTenant(@Param("transitionId") Long transitionId, @Param("tenant") com.example.demo.entity.Tenant tenant);

    @Query("SELECT p FROM ExecutorPermission p WHERE p.itemTypeConfiguration.id IN :itemTypeConfigurationIds AND p.itemTypeConfiguration.tenant = :tenant")
    List<ExecutorPermission> findAllByItemTypeConfigurationIdInAndTenant(@Param("itemTypeConfigurationIds") Collection<Long> itemTypeConfigurationIds,
                                                                         @Param("tenant") com.example.demo.entity.Tenant tenant);
}
