package com.example.demo.enums;

public enum ItemTypeSetRoleType {
    WORKER,         // Per ogni ItemType che compone l'ItemTypeSet
    OWNER,          // Per ogni WorkflowStatus di ogni Workflow associato agli ItemType
    FIELD_EDITOR,   // Per ogni FieldConfiguration di ogni FieldSet dell'ItemTypeSet
    CREATOR,        // Per ogni Workflow associato agli ItemType
    EXECUTOR,       // Per ogni Transition di ogni Workflow
    EDITOR,         // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
    VIEWER          // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
}
