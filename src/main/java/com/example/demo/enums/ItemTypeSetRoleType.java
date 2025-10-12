package com.example.demo.enums;

public enum ItemTypeSetRoleType {
    WORKERS,         // Per ogni ItemType che compone l'ItemTypeSet
    OWNERS,          // Per ogni WorkflowStatus di ogni Workflow associato agli ItemType
    FIELD_OWNERS,   // Field Owner - Per ogni FieldConfiguration di ogni FieldSet dell'ItemTypeSet
    CREATORS,        // Per ogni Workflow associato agli ItemType
    EXECUTORS,       // Per ogni Transition di ogni Workflow
    EDITORS,         // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
    VIEWERS          // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
}
