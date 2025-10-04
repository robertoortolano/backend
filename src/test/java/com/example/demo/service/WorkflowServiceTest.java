package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.enums.StatusCategory;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.metadata.WorkflowMetaMapper;
import com.example.demo.metadata.*;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;
    
    @Mock
    private WorkflowStatusRepository workflowStatusRepository;
    
    @Mock
    private WorkflowNodeRepository workflowNodeRepository;
    
    @Mock
    private TransitionRepository transitionRepository;
    
    @Mock
    private WorkflowEdgeRepository workflowEdgeRepository;
    
    @Mock
    private StatusLookup statusLookup;
    
    @Mock
    private WorkflowLookup workflowLookup;
    
    @Mock
    private DtoMapperFacade dtoMapper;
    
    @Mock
    private WorkflowMetaMapper workflowMetaMapper;
    
    @InjectMocks
    private WorkflowService workflowService;

    private Tenant tenant;
    private Status status1, status2, status3;
    private Workflow workflow;
    private WorkflowCreateDto createDto;
    private WorkflowUpdateDto updateDto;

    @BeforeEach
    void setUp() {
        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("test-tenant");

        // Setup statuses
        status1 = new Status();
        status1.setId(1L);
        status1.setName("To Do");
        status1.setTenant(tenant);

        status2 = new Status();
        status2.setId(2L);
        status2.setName("In Progress");
        status2.setTenant(tenant);

        status3 = new Status();
        status3.setId(3L);
        status3.setName("Done");
        status3.setTenant(tenant);

        // Setup workflow
        workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Test Workflow");
        workflow.setScope(ScopeType.GLOBAL);
        workflow.setTenant(tenant);
        workflow.setStatuses(new HashSet<>());
        workflow.setTransitions(new HashSet<>());

        // Setup create DTO
        createDto = new WorkflowCreateDto(
                "Test Workflow",
                1L, // initialStatusId
                Arrays.asList(
                        new WorkflowStatusCreateDto(1L, StatusCategory.BACKLOG, true, 
                                Arrays.asList(new TransitionCreateDto("t1", "Start Work", 1L, 2L))),
                        new WorkflowStatusCreateDto(2L, StatusCategory.PROGRESS, false,
                                Arrays.asList(new TransitionCreateDto("t2", "Complete", 2L, 3L))),
                        new WorkflowStatusCreateDto(3L, StatusCategory.COMPLETED, false, null)
                ),
                new ArrayList<>(), // nodes
                new ArrayList<>(), // transitions
                new ArrayList<>()  // edges
        );

        // Setup update DTO
        updateDto = new WorkflowUpdateDto(
                1L, // id
                "Updated Workflow",
                1L, // initialStatusId
                Arrays.asList(
                        new WorkflowStatusUpdateDto(1L, 1L, StatusCategory.BACKLOG, true, null),
                        new WorkflowStatusUpdateDto(2L, 2L, StatusCategory.PROGRESS, false, null),
                        new WorkflowStatusUpdateDto(3L, 3L, StatusCategory.COMPLETED, false, null)
                ),
                new ArrayList<>(), // nodes
                Arrays.asList(
                        new TransitionUpdateDto(1L, "temp1", "Start Work", 1L, 2L),
                        new TransitionUpdateDto(2L, "temp2", "Complete", 2L, 3L)
                ),
                new ArrayList<>() // edges
        );
    }

    @Test
    void testCreateGlobal_Success() {
        // Given
        when(statusLookup.getById(tenant, 1L)).thenReturn(status1);
        when(statusLookup.getById(tenant, 2L)).thenReturn(status2);
        when(statusLookup.getById(tenant, 3L)).thenReturn(status3);
        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflow);
        WorkflowStatus mockWorkflowStatus = new WorkflowStatus();
        mockWorkflowStatus.setStatus(status1);
        when(workflowStatusRepository.save(any(WorkflowStatus.class))).thenReturn(mockWorkflowStatus);
        when(workflowNodeRepository.save(any(WorkflowNode.class))).thenReturn(new WorkflowNode());
        when(transitionRepository.save(any(Transition.class))).thenReturn(new Transition());
        when(workflowEdgeRepository.save(any(WorkflowEdge.class))).thenReturn(new WorkflowEdge());
        when(workflowMetaMapper.toEntity(any(WorkflowNodeDto.class))).thenReturn(new WorkflowNode());
        when(workflowMetaMapper.toEntity(any(WorkflowEdgeDto.class))).thenReturn(new WorkflowEdge());
        WorkflowViewDto workflowViewDto = new WorkflowViewDto();
        workflowViewDto.setId(1L);
        workflowViewDto.setName("Test Workflow");
        workflowViewDto.setScope(ScopeType.GLOBAL);
        workflowViewDto.setDefaultWorkflow(false);
        when(dtoMapper.toWorkflowViewDto(any(Workflow.class))).thenReturn(workflowViewDto);

        // When
        WorkflowViewDto result = workflowService.createGlobal(createDto, tenant);

        // Then
        assertNotNull(result);
        verify(workflowRepository).save(any(Workflow.class));
        verify(workflowStatusRepository, atLeastOnce()).save(any(WorkflowStatus.class));
        verify(transitionRepository, atLeastOnce()).save(any(Transition.class));
    }

    @Test
    void testCreateGlobal_StatusNotFound_ThrowsException() {
        // Given
        when(statusLookup.getById(tenant, 1L)).thenThrow(new ApiException("Status not found"));

        // When & Then
        assertThrows(ApiException.class, () -> workflowService.createGlobal(createDto, tenant));
    }

    @Test
    void testUpdateWorkflow_Success() {
        // Given
        WorkflowStatus ws1 = new WorkflowStatus();
        ws1.setId(1L);
        ws1.setStatus(status1);
        ws1.setWorkflow(workflow);

        WorkflowStatus ws2 = new WorkflowStatus();
        ws2.setId(2L);
        ws2.setStatus(status2);
        ws2.setWorkflow(workflow);

        WorkflowStatus ws3 = new WorkflowStatus();
        ws3.setId(3L);
        ws3.setStatus(status3);
        ws3.setWorkflow(workflow);

        workflow.getStatuses().addAll(Arrays.asList(ws1, ws2, ws3));

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));
        when(statusLookup.getById(tenant, 1L)).thenReturn(status1);
        when(statusLookup.getById(tenant, 2L)).thenReturn(status2);
        when(statusLookup.getById(tenant, 3L)).thenReturn(status3);
        WorkflowStatus mockWorkflowStatus = new WorkflowStatus();
        mockWorkflowStatus.setStatus(status1);
        when(workflowStatusRepository.save(any(WorkflowStatus.class))).thenReturn(mockWorkflowStatus);
        when(workflowNodeRepository.save(any(WorkflowNode.class))).thenReturn(new WorkflowNode());
        when(transitionRepository.save(any(Transition.class))).thenReturn(new Transition());
        when(workflowEdgeRepository.save(any(WorkflowEdge.class))).thenReturn(new WorkflowEdge());
        when(workflowEdgeRepository.findByWorkflowIdAndTenant(1L, tenant)).thenReturn(new ArrayList<>());
        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflow);
        WorkflowViewDto updatedWorkflowViewDto = new WorkflowViewDto();
        updatedWorkflowViewDto.setId(1L);
        updatedWorkflowViewDto.setName("Updated Workflow");
        updatedWorkflowViewDto.setScope(ScopeType.GLOBAL);
        updatedWorkflowViewDto.setDefaultWorkflow(false);
        when(dtoMapper.toWorkflowViewDto(any(Workflow.class))).thenReturn(updatedWorkflowViewDto);

        // When
        WorkflowViewDto result = workflowService.updateWorkflow(1L, updateDto, tenant);

        // Then
        assertNotNull(result);
        verify(workflowRepository).save(any(Workflow.class));
        verify(workflowStatusRepository, atLeastOnce()).save(any(WorkflowStatus.class));
    }

    @Test
    void testUpdateWorkflow_WorkflowNotFound_ThrowsException() {
        // Given
        when(workflowRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> workflowService.updateWorkflow(1L, updateDto, tenant));
    }

    @Test
    void testUpdateWorkflow_WrongTenant_ThrowsSecurityException() {
        // Given
        Tenant otherTenant = new Tenant();
        otherTenant.setId(2L);
        workflow.setTenant(otherTenant);

        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        // When & Then
        assertThrows(SecurityException.class, () -> workflowService.updateWorkflow(1L, updateDto, tenant));
    }

    @Test
    void testDelete_Success() {
        // Given
        when(workflowLookup.getByIdEntity(tenant, 1L)).thenReturn(workflow);
        when(workflowLookup.isNotInAnyItemTypeSet(1L, tenant)).thenReturn(true);

        // When
        workflowService.delete(tenant, 1L);

        // Then
        verify(workflowRepository).delete(workflow);
    }

    @Test
    void testDelete_WorkflowInUse_ThrowsException() {
        // Given
        when(workflowLookup.getByIdEntity(tenant, 1L)).thenReturn(workflow);
        when(workflowLookup.isNotInAnyItemTypeSet(1L, tenant)).thenReturn(false);

        // When & Then
        assertThrows(ApiException.class, () -> workflowService.delete(tenant, 1L));
    }

}
