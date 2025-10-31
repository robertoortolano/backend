package com.example.demo.repository;

import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    Optional<Workflow> findByIdAndTenant(Long id, Tenant tenant);
    Optional<Workflow> findByTenantIdAndName(Long tenantId, String name);
    Optional<Workflow> findByTenantAndDefaultWorkflowTrue(Tenant tenant);
    List<Workflow> findAllByTenant(Tenant tenant);
    
    @Query("SELECT w FROM Workflow w WHERE w.tenant = :tenant AND w.scope = 'TENANT'")
    List<Workflow> findAllByTenantAndScopeTenant(@Param("tenant") Tenant tenant);
    
    List<Workflow> findAllByTenantAndProjectId(Tenant tenant, Long projectId);
    
    Optional<Workflow> findByIdAndTenantAndProjectId(Long id, Tenant tenant, Long projectId);

    @Query("""
    SELECT DISTINCT t.workflow FROM Transition t
    WHERE t.workflow.tenant = :tenant
      AND (t.fromStatus.status = :status OR t.toStatus.status = :status)
""")
    List<Workflow> findByStatusInTransitions(@Param("tenant") Tenant tenant, @Param("status") Status status);

}
