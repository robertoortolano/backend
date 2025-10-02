package com.example.demo.controller;

import com.example.demo.dto.TransitionCreateDto;
import com.example.demo.dto.TransitionViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.StatusService;
import com.example.demo.service.TransitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projectsAssociation/{projectId}/transitions")
@RequiredArgsConstructor
public class TransitionController {

    private final TransitionService transitionService;
    private final DtoMapperFacade dtoMapper;
    private final StatusService statusService;

    @PostMapping
    public TransitionViewDto createTransition(
            @Valid @RequestBody TransitionCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        return transitionService.createTransition(tenant, dto);
    }

    @DeleteMapping("/{transitionId}")
    public ResponseEntity<Void> deleteTransition(
            @PathVariable Long transitionId,
            @CurrentTenant Tenant tenant
    ) {
        transitionService.deleteTransition(tenant, transitionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/from/{statusId}")
    public ResponseEntity<List<Transition>> getOutgoing(
            @PathVariable Long statusId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(transitionService.getOutgoingTransitions(tenant, statusId));
    }

    @GetMapping("/to/{statusId}")
    public ResponseEntity<List<Transition>> getIncoming(
            @PathVariable Long statusId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(transitionService.getIncomingTransitions(tenant, statusId));
    }
}
