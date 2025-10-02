package com.example.demo.repository;

import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
