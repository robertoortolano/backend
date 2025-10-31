package com.example.demo.repository;

import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransitionRepository extends JpaRepository<Transition, Long> {
    List<Transition> findByFromStatus(WorkflowStatus status);
    List<Transition> findByToStatus(WorkflowStatus status);
    boolean existsByFromStatusAndToStatus(WorkflowStatus startStatus, WorkflowStatus endStatus);
    Optional<Transition> findByWorkflowAndFromStatusAndToStatus(Workflow workflow, WorkflowStatus from, WorkflowStatus to);
    List<Transition> findByWorkflow(Workflow workflow);
    Optional<Transition> findByWorkflowAndFromStatusAndToStatusAndName(Workflow workflow, WorkflowStatus fromStatus, WorkflowStatus toStatus, String name);
    
    /**
     * Trova una Transition per ID e Tenant (sicurezza)
     * Verifica che la Transition appartenga a un Workflow del Tenant specificato
     */
    @Query("SELECT t FROM Transition t JOIN t.workflow w WHERE t.id = :id AND w.tenant = :tenant")
    Optional<Transition> findByIdAndTenant(@Param("id") Long id, @Param("tenant") com.example.demo.entity.Tenant tenant);

}
