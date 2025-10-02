package com.example.demo.metadata;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, Long> {
    List<WorkflowNode> findByWorkflowIdAndTenant(Long workflowId, Tenant tenant);
    void deleteByWorkflowAndTenant(Workflow workflow, Tenant tenant);
}

