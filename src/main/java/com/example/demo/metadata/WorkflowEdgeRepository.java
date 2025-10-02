package com.example.demo.metadata;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, Long> {
    List<WorkflowEdge> findByWorkflowIdAndTenant(Long workflowId, Tenant tenant);
    void deleteByWorkflow(Workflow workflow);
}

