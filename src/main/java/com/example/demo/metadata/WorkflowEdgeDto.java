package com.example.demo.metadata;

public record WorkflowEdgeDto(
        Long id,           // id univoco dell'edge
        Long transitionId,
        String transitionTempId,
        Long sourceId,     // nodo sorgente
        Long targetId,     // nodo destinazione
        String sourcePosition, // lato sorgente: Top, Bottom, Left, Right
        String targetPosition  // lato target
) {}

