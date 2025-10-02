package com.example.demo.metadata;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "transition_id", nullable = false)
    private Transition transition;

    private Long sourceId;
    private Long targetId;
    private String sourcePosition;
    private String targetPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    private Tenant tenant;
}
