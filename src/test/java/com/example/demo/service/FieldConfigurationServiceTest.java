package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.fieldtype.FieldTypeDescriptor;
import com.example.demo.fieldtype.FieldTypeRegistry;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.FieldConfigurationRepository;
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
class FieldConfigurationServiceTest {

    @Mock
    private FieldConfigurationRepository fieldConfigurationRepository;
    
    @Mock
    private FieldTypeRegistry fieldTypeRegistry;
    
    @Mock
    private FieldConfigurationLookup fieldConfigurationLookup;
    
    @Mock
    private FieldLookup fieldLookup;
    
    @Mock
    private FieldSetLookup fieldSetLookup;
    
    @Mock
    private DtoMapperFacade dtoMapper;
    
    @Mock
    private FieldTypeDescriptor fieldTypeDescriptor;
    
    @InjectMocks
    private FieldConfigurationService fieldConfigurationService;

    private Tenant tenant;
    private Field field;
    private FieldConfiguration fieldConfiguration;
    private FieldConfigurationCreateDto createDto;
    private FieldConfigurationUpdateDto updateDto;

    @BeforeEach
    void setUp() {
        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("test-tenant");

        // Setup field
        field = new Field();
        field.setId(1L);
        field.setName("Test Field");
        field.setTenant(tenant);

        // Setup field configuration
        fieldConfiguration = new FieldConfiguration();
        fieldConfiguration.setId(1L);
        fieldConfiguration.setName("Test Configuration");
        fieldConfiguration.setDescription("Test Description");
        fieldConfiguration.setField(field);
        fieldConfiguration.setFieldType(FieldType.SHORT_TEXT);
        fieldConfiguration.setScope(ScopeType.TENANT);
        fieldConfiguration.setTenant(tenant);
        fieldConfiguration.setDefaultFieldConfiguration(false);
        fieldConfiguration.setOptions(new HashSet<>());

        // Setup create DTO
        createDto = new FieldConfigurationCreateDto(
                "Test Configuration",
                "Test Description",
                "test-alias",
                1L, // fieldId
                FieldType.SHORT_TEXT,
                Set.of(
                        new FieldOptionCreateDto("Option 1", "value1", 0),
                        new FieldOptionCreateDto("Option 2", "value2", 1)
                )
        );

        // Setup update DTO
        updateDto = new FieldConfigurationUpdateDto(
                "Updated Configuration",
                "Updated Description",
                "updated-alias",
                1L, // fieldId
                FieldType.SINGLE_SELECT,
                Set.of(
                        new FieldOptionUpdateDto(1L, "Updated Option 1", "updated_value1", true, 0),
                        new FieldOptionUpdateDto(2L, "Updated Option 2", "updated_value2", true, 1)
                )
        );
    }

    @Test
    void testCreateGlobalFieldConfiguration_Success() {
        // Given
        when(fieldLookup.getById(1L, tenant)).thenReturn(field);
        when(fieldConfigurationRepository.save(any(FieldConfiguration.class))).thenReturn(fieldConfiguration);
        when(dtoMapper.toFieldOptionEntitySetFromCreate(any())).thenReturn(new HashSet<>());
        when(dtoMapper.toFieldConfigurationViewDto(any(FieldConfiguration.class)))
                .thenReturn(new FieldConfigurationViewDto(1L, "Test Configuration", "Test Description", 1L, "Test Field", "test-alias", false, null, ScopeType.TENANT, new HashSet<>(), new ArrayList<>()));

        // When
        FieldConfigurationViewDto result = fieldConfigurationService.createGlobalFieldConfiguration(createDto, tenant);

        // Then
        assertNotNull(result);
        verify(fieldLookup).getById(1L, tenant);
        verify(fieldConfigurationRepository).save(any(FieldConfiguration.class));
        verify(dtoMapper).toFieldConfigurationViewDto(any(FieldConfiguration.class));
    }

    @Test
    void testGetAllGlobalFieldConfigurations_Success() {
        // Given
        List<FieldConfiguration> configurations = Arrays.asList(fieldConfiguration);
        when(fieldConfigurationRepository.findByTenantAndScope(tenant, ScopeType.TENANT)).thenReturn(configurations);
        when(dtoMapper.toFieldConfigurationViewDtos(configurations))
                .thenReturn(Arrays.asList(new FieldConfigurationViewDto(1L, "Test Configuration", "Test Description", 1L, "Test Field", "test-alias", false, null, ScopeType.TENANT, new HashSet<>(), new ArrayList<>())));

        // When
        List<FieldConfigurationViewDto> result = fieldConfigurationService.getAllGlobalFieldConfigurations(tenant);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(fieldConfigurationRepository).findByTenantAndScope(tenant, ScopeType.TENANT);
    }

    @Test
    void testGetById_Success() {
        // Given
        when(fieldConfigurationLookup.getById(1L, tenant)).thenReturn(fieldConfiguration);
        when(dtoMapper.toFieldConfigurationViewDto(fieldConfiguration))
                .thenReturn(new FieldConfigurationViewDto(1L, "Test Configuration", "Test Description", 1L, "Test Field", "test-alias", false, null, ScopeType.TENANT, new HashSet<>(), new ArrayList<>()));

        // When
        FieldConfigurationViewDto result = fieldConfigurationService.getById(1L, tenant);

        // Then
        assertNotNull(result);
        verify(fieldConfigurationLookup).getById(1L, tenant);
    }

    @Test
    void testUpdateConfiguration_Success() {
        // Given
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldConfiguration));
        when(fieldTypeRegistry.getDescriptor(FieldType.SINGLE_SELECT)).thenReturn(fieldTypeDescriptor);
        when(fieldTypeDescriptor.isSupportsOptions()).thenReturn(true);
        when(fieldLookup.getById(1L, tenant)).thenReturn(field);
        when(dtoMapper.toFieldOptionEntitySetFromUpdate(any())).thenReturn(new HashSet<>());
        when(fieldConfigurationRepository.save(any(FieldConfiguration.class))).thenReturn(fieldConfiguration);
        when(dtoMapper.toFieldConfigurationViewDtos(any())).thenReturn(Arrays.asList(new FieldConfigurationViewDto(1L, "Updated Configuration", "Updated Description", 1L, "Test Field", "updated-alias", false, null, ScopeType.TENANT, new HashSet<>(), new ArrayList<>())));

        // When
        FieldConfigurationViewDto result = fieldConfigurationService.updateConfiguration(tenant, 1L, updateDto);

        // Then
        assertNotNull(result);
        verify(fieldConfigurationRepository).findByIdAndTenant(1L, tenant);
        verify(fieldConfigurationRepository).save(any(FieldConfiguration.class));
    }

    @Test
    void testUpdateConfiguration_NotFound_ThrowsException() {
        // Given
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldConfigurationService.updateConfiguration(tenant, 1L, updateDto));
    }

    @Test
    void testUpdateConfiguration_DefaultConfiguration_ThrowsException() {
        // Given
        fieldConfiguration.setDefaultFieldConfiguration(true);
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldConfiguration));

        // When & Then
        assertThrows(ApiException.class, () -> fieldConfigurationService.updateConfiguration(tenant, 1L, updateDto));
    }

    @Test
    void testUpdateConfiguration_FieldTypeNotSupportsOptions_ClearsOptions() {
        // Given
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldConfiguration));
        when(fieldTypeRegistry.getDescriptor(any(FieldType.class))).thenReturn(fieldTypeDescriptor);
        when(fieldTypeDescriptor.isSupportsOptions()).thenReturn(false);
        when(fieldLookup.getById(1L, tenant)).thenReturn(field);
        when(fieldConfigurationRepository.save(any(FieldConfiguration.class))).thenReturn(fieldConfiguration);
        when(dtoMapper.toFieldConfigurationViewDtos(any())).thenReturn(Arrays.asList(new FieldConfigurationViewDto(1L, "Updated Configuration", "Updated Description", 1L, "Test Field", "updated-alias", false, null, ScopeType.TENANT, new HashSet<>(), new ArrayList<>())));

        // When
        FieldConfigurationViewDto result = fieldConfigurationService.updateConfiguration(tenant, 1L, updateDto);

        // Then
        assertNotNull(result);
        assertTrue(fieldConfiguration.getOptions().isEmpty());
    }

    @Test
    void testDeleteFieldConfiguration_Success() {
        // Given
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldConfiguration));

        // When
        fieldConfigurationService.deleteFieldConfiguration(tenant, 1L);

        // Then
        verify(fieldConfigurationRepository).deleteByIdAndTenant(1L, tenant);
    }

    @Test
    void testDeleteFieldConfiguration_NotFound_ThrowsException() {
        // Given
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldConfigurationService.deleteFieldConfiguration(tenant, 1L));
    }

    @Test
    void testDeleteFieldConfiguration_DefaultConfiguration_ThrowsException() {
        // Given
        fieldConfiguration.setDefaultFieldConfiguration(true);
        when(fieldConfigurationRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldConfiguration));

        // When & Then
        assertThrows(ApiException.class, () -> fieldConfigurationService.deleteFieldConfiguration(tenant, 1L));
    }

    @Test
    void testIsFieldInAnyFieldConfiguration_Success() {
        // Given
        when(fieldConfigurationRepository.existsByFieldIdAndTenant(1L, tenant)).thenReturn(true);

        // When
        boolean result = fieldConfigurationService.isFieldInAnyFieldConfiguration(tenant, 1L);

        // Then
        assertTrue(result);
        verify(fieldConfigurationRepository).existsByFieldIdAndTenant(1L, tenant);
    }

    @Test
    void testGetProjectsUsingField_Success() {
        // Given
        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");

        FieldConfiguration config = new FieldConfiguration();
        config.setProject(project);

        when(fieldConfigurationRepository.findByFieldIdAndProjectIsNotNullAndTenant(1L, tenant))
                .thenReturn(Arrays.asList(config));

        // When
        Set<Project> result = fieldConfigurationService.getProjectsUsingField(tenant, 1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(project));
    }

    @Test
    void testIsFieldConfigurationInAnyFieldSet_Success() {
        // Given
        when(fieldSetLookup.isFieldConfigurationInAnyFieldSet(1L, tenant)).thenReturn(true);

        // When
        boolean result = fieldConfigurationService.isFieldConfigurationInAnyFieldSet(1L, tenant);

        // Then
        assertTrue(result);
        verify(fieldSetLookup).isFieldConfigurationInAnyFieldSet(1L, tenant);
    }
}
