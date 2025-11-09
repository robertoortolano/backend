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

    /**
     * Trova tutte le Transition che partono da un WorkflowStatus, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT t FROM Transition t JOIN t.fromStatus fs JOIN fs.workflow w WHERE fs = :status AND w.tenant = :tenant")
    List<Transition> findByFromStatusAndTenant(@Param("status") WorkflowStatus status, @Param("tenant") com.example.demo.entity.Tenant tenant);

    /**
     * Trova tutte le Transition che arrivano a un WorkflowStatus, filtrate per Tenant (sicurezza)
     */
    @Query("SELECT t FROM Transition t JOIN t.toStatus ts JOIN ts.workflow w WHERE ts = :status AND w.tenant = :tenant")
    List<Transition> findByToStatusAndTenant(@Param("status") WorkflowStatus status, @Param("tenant") com.example.demo.entity.Tenant tenant);

    /**
     * Trova tutte le Transition di un Workflow, filtrate per Tenant (sicurezza)
     */
    @Query("""
        SELECT DISTINCT t
        FROM Transition t
        LEFT JOIN FETCH t.fromStatus
        LEFT JOIN FETCH t.toStatus
        WHERE t.workflow = :workflow AND t.workflow.tenant = :tenant
    """)
    List<Transition> findByWorkflowAndTenant(@Param("workflow") Workflow workflow, @Param("tenant") com.example.demo.entity.Tenant tenant);

    /**
     * Trova Transition per ID, filtrata per Tenant (sicurezza)
     */
    @Query("SELECT t FROM Transition t JOIN t.workflow w WHERE t.id = :transitionId AND w.tenant = :tenant")
    Optional<Transition> findByTransitionIdAndTenant(@Param("transitionId") Long transitionId, @Param("tenant") com.example.demo.entity.Tenant tenant);

}
