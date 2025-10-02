package com.example.demo.repository;

import com.example.demo.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, Long> {
    boolean existsByStatusId(Long statusId);

    @Query("""
    SELECT DISTINCT ws.workflow.project
    FROM WorkflowStatus ws
    WHERE ws.status.id = :statusId
      AND ws.workflow.project.tenant.id = :tenantId
""")
    Set<Project> findProjectsUsingStatus(@Param("statusId") Long statusId, @Param("tenantId") Long tenantId);
    Optional<WorkflowStatus> findByWorkflowAndStatus(Workflow workflow, Status status);

    @Query("""
    SELECT DISTINCT ws.workflow
    FROM WorkflowStatus ws
    WHERE ws.status = :status
      AND ws.workflow.tenant = :tenant
""")
    List<Workflow> findDistinctWorkflowsByStatusAndTenant(
            @Param("status") Status status,
            @Param("tenant") Tenant tenant
    );


}
