package com.example.demo.repository;

import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldOwnerPermissionRepository extends JpaRepository<FieldOwnerPermission, Long> {
    List<FieldOwnerPermission> findByItemTypeConfigurationId(Long itemTypeConfigurationId);
    List<FieldOwnerPermission> findAllByItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration);
    boolean existsByItemTypeConfigurationIdAndFieldId(Long itemTypeConfigurationId, Long fieldId);
    
    /**
     * Trova FieldOwnerPermission per ItemTypeConfiguration e Field
     * IMPORTANTE: Carica anche i ruoli associati (JOIN FETCH) per evitare problemi di lazy loading
     */
    @Query("SELECT p FROM FieldOwnerPermission p " +
           "LEFT JOIN FETCH p.assignedRoles " +
           "WHERE p.itemTypeConfiguration = :config AND p.field.id = :fieldId")
    FieldOwnerPermission findByItemTypeConfigurationAndFieldId(
        @Param("config") ItemTypeConfiguration config, 
        @Param("fieldId") Long fieldId
    );
}
