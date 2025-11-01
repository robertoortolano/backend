package com.example.demo.repository;

import com.example.demo.entity.ProjectItemTypeSetRoleGrant;
import com.example.demo.entity.ItemTypeSetRole;
import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectItemTypeSetRoleGrantRepository extends JpaRepository<ProjectItemTypeSetRoleGrant, Long> {
    
    Optional<ProjectItemTypeSetRoleGrant> findByItemTypeSetRoleAndProjectAndTenant(
        ItemTypeSetRole itemTypeSetRole, 
        Project project, 
        Tenant tenant
    );
    
    Optional<ProjectItemTypeSetRoleGrant> findByItemTypeSetRoleIdAndProjectIdAndTenantId(
        Long itemTypeSetRoleId,
        Long projectId,
        Long tenantId
    );
    
    List<ProjectItemTypeSetRoleGrant> findByProjectAndTenant(Project project, Tenant tenant);
    
    @Query("""
        SELECT pig FROM ProjectItemTypeSetRoleGrant pig
        JOIN pig.itemTypeSetRole itsr
        WHERE itsr.itemTypeSet.id = :itemTypeSetId
          AND pig.project.id = :projectId
          AND pig.tenant.id = :tenantId
    """)
    List<ProjectItemTypeSetRoleGrant> findByItemTypeSetIdAndProjectIdAndTenantId(
        @Param("itemTypeSetId") Long itemTypeSetId,
        @Param("projectId") Long projectId,
        @Param("tenantId") Long tenantId
    );
    
    void deleteByItemTypeSetRoleIdAndProjectIdAndTenantId(
        Long itemTypeSetRoleId,
        Long projectId,
        Long tenantId
    );
    
    boolean existsByItemTypeSetRoleIdAndProjectIdAndTenantId(
        Long itemTypeSetRoleId,
        Long projectId,
        Long tenantId
    );
}

