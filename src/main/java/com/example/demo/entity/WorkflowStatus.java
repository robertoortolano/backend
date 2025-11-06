package com.example.demo.entity;

import com.example.demo.enums.StatusCategory;
import com.example.demo.metadata.WorkflowNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    @ManyToOne(optional = false)
    @JoinColumn(name = "status_id")
    private Status status;

    @OneToOne(mappedBy = "workflowStatus", cascade = CascadeType.ALL, orphanRemoval = true)
    private WorkflowNode node;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCategory statusCategory;

    @Column(nullable = false)
    private boolean initial = false;

    // Transizioni in uscita da questo status
    @OneToMany(mappedBy = "fromStatus", cascade = CascadeType.ALL)
    private Set<Transition> outgoingTransitions = new HashSet<>();

    // Transizioni in entrata verso questo status
    @OneToMany(mappedBy = "toStatus", cascade = CascadeType.ALL)
    private Set<Transition> incomingTransitions = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "workflowstatus_role",
            joinColumns = @JoinColumn(name = "workflowstatus_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> owners = new HashSet<>();

    public void setNode(WorkflowNode node) {
        this.node = node;
        if (node != null) {
            node.setWorkflowStatus(this);
        }
    }

}
