package com.example.demo.service;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleCreateDTO;
import com.example.demo.dto.ItemTypeSetRoleGrantCreateDTO;
import com.example.demo.dto.ItemTypeSetRoleGrantDTO;
import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.enums.ScopeType;
import com.example.demo.mapper.ItemTypeSetRoleMapper;
import com.example.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemTypeSetRoleServiceTest {

    @Mock
    private ItemTypeSetRoleRepository itemTypeSetRoleRepository;
    
    @Mock
    private ItemTypeSetRoleGrantRepository itemTypeSetRoleGrantRepository;
    
    @Mock
    private ItemTypeSetRepository itemTypeSetRepository;
    
    @Mock
    private GrantRepository grantRepository;
    
    @Mock
    private TenantRepository tenantRepository;
    
    @Mock
    private ItemTypeSetRoleMapper itemTypeSetRoleMapper;
    
    @InjectMocks
    private ItemTypeSetRoleService itemTypeSetRoleService;

    private ItemTypeSet itemTypeSet;
    private Tenant tenant;
    private ItemType itemType;
    private Workflow workflow;
    private FieldSet fieldSet;
    private FieldConfiguration fieldConfig;

    @BeforeEach
    void setUp() {
        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("Test Tenant");

        // Setup ItemType
        itemType = new ItemType();
        itemType.setId(1L);
        itemType.setName("Bug");
        itemType.setTenant(tenant);

        // Setup Workflow
        workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Development Workflow");
        workflow.setScope(ScopeType.PROJECT);
        workflow.setTenant(tenant);

        // Setup FieldConfiguration
        fieldConfig = new FieldConfiguration();
        fieldConfig.setId(1L);
        fieldConfig.setName("Description");
        fieldConfig.setScope(ScopeType.PROJECT);
        fieldConfig.setTenant(tenant);

        // Setup FieldSet
        fieldSet = new FieldSet();
        fieldSet.setId(1L);
        fieldSet.setName("Bug Fields");
        fieldSet.setScope(ScopeType.PROJECT);
        fieldSet.setTenant(tenant);

        // Setup ItemTypeSet
        itemTypeSet = new ItemTypeSet();
        itemTypeSet.setId(1L);
        itemTypeSet.setName("Software Project");
        itemTypeSet.setScope(ScopeType.PROJECT);
        itemTypeSet.setTenant(tenant);

        // Setup ItemTypeConfiguration
        ItemTypeConfiguration config = new ItemTypeConfiguration();
        config.setId(1L);
        config.setItemType(itemType);
        config.setWorkflow(workflow);
        config.setFieldSet(fieldSet);
        config.setTenant(tenant);

        Set<ItemTypeConfiguration> configurations = new HashSet<>();
        configurations.add(config);
        itemTypeSet.setItemTypeConfigurations(configurations);
    }

    @Test
    void testCreateRolesForItemTypeSet() {
        // Given
        when(itemTypeSetRepository.findById(1L)).thenReturn(Optional.of(itemTypeSet));
        when(itemTypeSetRoleRepository.save(any(ItemTypeSetRole.class))).thenReturn(new ItemTypeSetRole());

        // When
        itemTypeSetRoleService.createRolesForItemTypeSet(1L, tenant);

        // Then
        // Verifica che siano stati creati i ruoli per ogni tipo
        verify(itemTypeSetRoleRepository, atLeastOnce()).save(any(ItemTypeSetRole.class));
    }

    @Test
    void testAssignGrantToRole() {
        // Given
        ItemTypeSetRole role = new ItemTypeSetRole();
        role.setId(1L);
        role.setRoleType(ItemTypeSetRoleType.WORKERS);
        role.setItemTypeSet(itemTypeSet);
        role.setTenant(tenant);

        Grant grant = new Grant();
        grant.setId(1L);
        grant.setUsers(new HashSet<>());
        grant.setGroups(new HashSet<>());
        grant.setNegatedUsers(new HashSet<>());
        grant.setNegatedGroups(new HashSet<>());

        ItemTypeSetRoleGrant roleGrant = new ItemTypeSetRoleGrant();
        roleGrant.setId(1L);
        roleGrant.setItemTypeSetRole(role);
        roleGrant.setGrant(grant);
        roleGrant.setTenant(tenant);

        when(itemTypeSetRoleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(grantRepository.findById(1L)).thenReturn(Optional.of(grant));
        when(itemTypeSetRoleGrantRepository.existsByItemTypeSetRoleIdAndGrantIdAndTenantId(1L, 1L, 1L)).thenReturn(false);
        when(itemTypeSetRoleGrantRepository.save(any(ItemTypeSetRoleGrant.class))).thenReturn(roleGrant);

        ItemTypeSetRoleGrantCreateDTO createDTO = ItemTypeSetRoleGrantCreateDTO.builder()
                .itemTypeSetRoleId(1L)
                .grantId(1L)
                .build();

        // When
        ItemTypeSetRoleGrantDTO result = itemTypeSetRoleService.assignGrantToRole(createDTO, tenant);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getItemTypeSetRoleId());
        assertEquals(1L, result.getGrantId());
        verify(itemTypeSetRoleGrantRepository).save(any(ItemTypeSetRoleGrant.class));
    }

    @Test
    void testGetRolesByItemTypeSet() {
        // Given
        ItemTypeSetRole role = new ItemTypeSetRole();
        role.setId(1L);
        role.setRoleType(ItemTypeSetRoleType.WORKERS);
        role.setName("Worker for Bug");
        role.setItemTypeSet(itemTypeSet);
        role.setTenant(tenant);

        ItemTypeSetRoleDTO roleDTO = ItemTypeSetRoleDTO.builder()
                .id(1L)
                .roleType(ItemTypeSetRoleType.WORKERS)
                .name("Worker for Bug")
                .itemTypeSetId(1L)
                .tenantId(1L)
                .build();

        List<ItemTypeSetRole> roles = Arrays.asList(role);
        when(itemTypeSetRoleRepository.findByItemTypeSetIdAndTenantId(1L, 1L)).thenReturn(roles);
        when(itemTypeSetRoleMapper.toDTO(role)).thenReturn(roleDTO);

        // When
        List<ItemTypeSetRoleDTO> result = itemTypeSetRoleService.getRolesByItemTypeSet(1L, tenant);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ItemTypeSetRoleType.WORKERS, result.get(0).getRoleType());
    }

    @Test
    void testCreateRole() {
        // Given
        ItemTypeSetRoleCreateDTO createDTO = ItemTypeSetRoleCreateDTO.builder()
                .roleType(ItemTypeSetRoleType.WORKERS)
                .name("Test Role")
                .description("Test Description")
                .itemTypeSetId(1L)
                .relatedEntityType("ItemType")
                .relatedEntityId(1L)
                .build();

        ItemTypeSetRole savedRole = new ItemTypeSetRole();
        savedRole.setId(1L);
        savedRole.setRoleType(ItemTypeSetRoleType.WORKERS);
        savedRole.setName("Test Role");
        savedRole.setItemTypeSet(itemTypeSet);
        savedRole.setTenant(tenant);

        ItemTypeSetRoleDTO roleDTO = new ItemTypeSetRoleDTO();
        roleDTO.setId(1L);
        roleDTO.setRoleType(ItemTypeSetRoleType.WORKERS);
        roleDTO.setName("Test Role");

        when(itemTypeSetRepository.findById(1L)).thenReturn(Optional.of(itemTypeSet));
        when(itemTypeSetRoleRepository.save(any(ItemTypeSetRole.class))).thenReturn(savedRole);
        when(itemTypeSetRoleMapper.toDTO(savedRole)).thenReturn(roleDTO);

        // When
        ItemTypeSetRoleDTO result = itemTypeSetRoleService.createRole(createDTO, tenant);

        // Then
        assertNotNull(result);
        assertEquals(ItemTypeSetRoleType.WORKERS, result.getRoleType());
        assertEquals("Test Role", result.getName());
        verify(itemTypeSetRoleRepository).save(any(ItemTypeSetRole.class));
    }

    @Test
    void testGetRolesByType() {
        // Given
        ItemTypeSetRole role = new ItemTypeSetRole();
        role.setId(1L);
        role.setRoleType(ItemTypeSetRoleType.WORKERS);
        role.setItemTypeSet(itemTypeSet);
        role.setTenant(tenant);

        ItemTypeSetRoleDTO roleDTO = new ItemTypeSetRoleDTO();
        roleDTO.setId(1L);
        roleDTO.setRoleType(ItemTypeSetRoleType.WORKERS);

        List<ItemTypeSetRole> roles = Arrays.asList(role);
        when(itemTypeSetRoleRepository.findRolesByItemTypeSetAndType(1L, ItemTypeSetRoleType.WORKERS, 1L)).thenReturn(roles);
        when(itemTypeSetRoleMapper.toDTO(role)).thenReturn(roleDTO);

        // When
        List<ItemTypeSetRoleDTO> result = itemTypeSetRoleService.getRolesByType(1L, ItemTypeSetRoleType.WORKERS, tenant);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ItemTypeSetRoleType.WORKERS, result.get(0).getRoleType());
    }

    @Test
    void testRemoveGrantFromRole() {
        // When
        itemTypeSetRoleService.removeGrantFromRole(1L, 1L, tenant);

        // Then
        verify(itemTypeSetRoleGrantRepository).deleteByItemTypeSetRoleIdAndGrantIdAndTenantId(1L, 1L, 1L);
    }
}
