package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DefaultConfig {
    private List<ItemType> itemTypes;
    private List<ItemTypeSet> itemTypeSets;
    private List<Field> fields;
    private List<FieldSet> fieldSets;
    private List<Status> status;
    private List<Workflow> workflow;
    private List<WorkflowNode> workflowNodes;
    private List<WorkflowEdge> workflowEdges;
    private List<Permission> permissions;

    @Getter
    @Setter
    public static class ItemType {
        private String name;
        private String description;
        private String category;
    }

    @Getter
    @Setter
    public static class ItemTypeSet {
        private String name;
        private List<String> itemTypes;
    }

    @Getter
    @Setter
    public static class Field {
        private String name;
        private String fieldType;
        private String description;
        private List<String> options; // NUOVO: valori predefiniti per checkbox, radio, liste...
    }

    @Getter
    @Setter
    public static class FieldSet {
        private String name;
        private List<Field> fields;
        private String description;
    }

    @Getter
    @Setter
    public static class Status {
        private String name;
        private String category;
    }

    @Getter
    @Setter
    public static class Workflow {
        private String name;
        private List<Transition> transitions;
        private String initialStatus;
    }

    @Getter
    @Setter
    public static class Transition {
        private String fromStatus;
        private String toStatus;
    }

    @Getter
    @Setter
    public static class WorkflowNode {
        private String nodeId;
        private Double positionX;
        private Double positionY;
    }

    @Getter
    @Setter
    public static class WorkflowEdge {
        private String sourceId;
        private String sourcePosition;
        private String targetId;
        private String targetPosition;
    }

    @Getter
    @Setter
    public static class Permission {
        private String name;
        private String description;
    }

}
