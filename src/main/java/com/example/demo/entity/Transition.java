package com.example.demo.entity;

import com.example.demo.metadata.WorkflowEdge;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Transition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToOne(mappedBy = "transition", cascade = CascadeType.ALL, orphanRemoval = true)
    private WorkflowEdge edge;

    @ManyToOne
    @JoinColumn(name = "from_status_id")
    private WorkflowStatus fromStatus;

    @ManyToOne
    @JoinColumn(name = "to_status_id")
    private WorkflowStatus toStatus;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    public void setEdge(WorkflowEdge edge) {
        this.edge = edge;
        if (edge != null) {
            edge.setTransition(this);
        }
    }

}
