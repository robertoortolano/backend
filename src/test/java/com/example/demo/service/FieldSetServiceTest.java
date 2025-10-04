package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
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
class FieldSetServiceTest {

    @Mock
    private FieldSetRepository fieldSetRepository;
    
    @Mock
    private FieldSetEntryRepository fieldSetEntryRepository;
    
    @Mock
    private FieldConfigurationLookup fieldConfigurationLookup;
    
    @Mock
    private ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    
    @Mock
    private FieldSetLookup fieldSetLookup;
    
    @Mock
    private DtoMapperFacade dtoMapper;
    
    @InjectMocks
    private FieldSetService fieldSetService;

    private Tenant tenant;
    private FieldConfiguration fieldConfig1, fieldConfig2;
    private FieldSet fieldSet;
    private FieldSetEntry entry1, entry2;
    private FieldSetCreateDto createDto;
    private FieldSetViewDto viewDto;

    @BeforeEach
    void setUp() {
        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("test-tenant");

        // Setup field configurations
        fieldConfig1 = new FieldConfiguration();
        fieldConfig1.setId(1L);
        fieldConfig1.setName("Field Config 1");
        fieldConfig1.setFieldType(FieldType.SHORT_TEXT);
        fieldConfig1.setTenant(tenant);

        fieldConfig2 = new FieldConfiguration();
        fieldConfig2.setId(2L);
        fieldConfig2.setName("Field Config 2");
        fieldConfig2.setFieldType(FieldType.SINGLE_SELECT);
        fieldConfig2.setTenant(tenant);

        // Setup field set
        fieldSet = new FieldSet();
        fieldSet.setId(1L);
        fieldSet.setName("Test Field Set");
        fieldSet.setDescription("Test Description");
        fieldSet.setScope(ScopeType.GLOBAL);
        fieldSet.setTenant(tenant);
        fieldSet.setDefaultFieldSet(false);
        fieldSet.setFieldSetEntries(new ArrayList<>());

        // Setup field set entries
        entry1 = new FieldSetEntry();
        entry1.setId(1L);
        entry1.setFieldSet(fieldSet);
        entry1.setFieldConfiguration(fieldConfig1);
        entry1.setOrderIndex(0);

        entry2 = new FieldSetEntry();
        entry2.setId(2L);
        entry2.setFieldSet(fieldSet);
        entry2.setFieldConfiguration(fieldConfig2);
        entry2.setOrderIndex(1);

        fieldSet.getFieldSetEntries().addAll(Arrays.asList(entry1, entry2));

        // Setup create DTO
        createDto = new FieldSetCreateDto(
                "Test Field Set",
                "Test Description",
                Arrays.asList(
                        new FieldSetEntryCreateDto(1L, 0),
                        new FieldSetEntryCreateDto(2L, 1)
                )
        );

        // Setup view DTO
        viewDto = new FieldSetViewDto(1L, "Test Field Set", "Test Description", ScopeType.GLOBAL, false, new ArrayList<>());
    }

    @Test
    void testCreateGlobalFieldSet_Success() {
        // Given
        when(fieldConfigurationLookup.getAll(Arrays.asList(1L, 2L), tenant))
                .thenReturn(Arrays.asList(fieldConfig1, fieldConfig2));
        when(fieldSetRepository.save(any(FieldSet.class))).thenReturn(fieldSet);
        when(dtoMapper.toFieldSetViewDto(fieldSet)).thenReturn(viewDto);

        // When
        FieldSetViewDto result = fieldSetService.createGlobalFieldSet(createDto, tenant);

        // Then
        assertNotNull(result);
        verify(fieldConfigurationLookup).getAll(Arrays.asList(1L, 2L), tenant);
        verify(fieldSetRepository).save(any(FieldSet.class));
        verify(dtoMapper).toFieldSetViewDto(fieldSet);
    }

    @Test
    void testCreateGlobalFieldSet_ConfigurationNotFound_ThrowsException() {
        // Given
        when(fieldConfigurationLookup.getAll(Arrays.asList(1L, 2L), tenant))
                .thenReturn(Arrays.asList(fieldConfig1)); // Missing fieldConfig2

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.createGlobalFieldSet(createDto, tenant));
    }

    @Test
    void testUpdateFieldSet_Success() {
        // Given
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));
        when(fieldSetRepository.save(any(FieldSet.class))).thenReturn(fieldSet);
        when(dtoMapper.toFieldSetViewDto(fieldSet)).thenReturn(viewDto);

        // When
        FieldSetViewDto result = fieldSetService.updateFieldSet(tenant, 1L, createDto);

        // Then
        assertNotNull(result);
        verify(fieldSetRepository).findByIdAndTenant(1L, tenant);
        verify(fieldSetRepository).save(any(FieldSet.class));
    }

    @Test
    void testUpdateFieldSet_NotFound_ThrowsException() {
        // Given
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.updateFieldSet(tenant, 1L, createDto));
    }

    @Test
    void testUpdateFieldSet_DefaultFieldSet_ThrowsException() {
        // Given
        fieldSet.setDefaultFieldSet(true);
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.updateFieldSet(tenant, 1L, createDto));
    }

    @Test
    void testGetGlobalFieldSets_Success() {
        // Given
        List<FieldSet> fieldSets = Arrays.asList(fieldSet);
        when(fieldSetRepository.findByTenantAndScope(tenant, ScopeType.GLOBAL)).thenReturn(fieldSets);
        when(dtoMapper.toFieldSetViewDto(fieldSet)).thenReturn(viewDto);

        // When
        List<FieldSetViewDto> result = fieldSetService.getGlobalFieldSets(tenant);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(fieldSetRepository).findByTenantAndScope(tenant, ScopeType.GLOBAL);
    }

    @Test
    void testGetById_Success() {
        // Given
        when(fieldSetLookup.getById(1L, tenant)).thenReturn(fieldSet);
        when(dtoMapper.toFieldSetViewDto(fieldSet)).thenReturn(viewDto);

        // When
        FieldSetViewDto result = fieldSetService.getById(tenant, 1L);

        // Then
        assertNotNull(result);
        verify(fieldSetLookup).getById(1L, tenant);
    }

    @Test
    void testDelete_Success() {
        // Given
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));

        // When
        fieldSetService.delete(tenant, 1L);

        // Then
        verify(fieldSetRepository).deleteByIdAndTenant(1L, tenant);
    }

    @Test
    void testDelete_NotFound_ThrowsException() {
        // Given
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.delete(tenant, 1L));
    }

    @Test
    void testDelete_DefaultFieldSet_ThrowsException() {
        // Given
        fieldSet.setDefaultFieldSet(true);
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.delete(tenant, 1L));
    }

    @Test
    void testReorderEntries_Success() {
        // Given
        List<EntryOrderDto> newOrder = Arrays.asList(
                new EntryOrderDto(1L, 1),
                new EntryOrderDto(2L, 0)
        );
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));

        // When
        fieldSetService.reorderEntries(tenant, 1L, newOrder);

        // Then
        verify(fieldSetRepository).findByIdAndTenant(1L, tenant);
        assertEquals(1, entry1.getOrderIndex());
        assertEquals(0, entry2.getOrderIndex());
    }

    @Test
    void testReorderEntries_NotFound_ThrowsException() {
        // Given
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.reorderEntries(tenant, 1L, Arrays.asList()));
    }

    @Test
    void testReorderEntries_DefaultFieldSet_ThrowsException() {
        // Given
        fieldSet.setDefaultFieldSet(true);
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.reorderEntries(tenant, 1L, Arrays.asList()));
    }

    @Test
    void testDeleteEntry_Success() {
        // Given
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.of(entry1));

        // When
        fieldSetService.deleteEntry(tenant, 1L);

        // Then
        verify(fieldSetEntryRepository).delete(entry1);
    }

    @Test
    void testDeleteEntry_NotFound_ThrowsException() {
        // Given
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.deleteEntry(tenant, 1L));
    }

    @Test
    void testDeleteEntry_DefaultFieldSet_ThrowsException() {
        // Given
        fieldSet.setDefaultFieldSet(true);
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.of(entry1));

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.deleteEntry(tenant, 1L));
    }

    @Test
    void testAddEntry_Success() {
        // Given
        FieldSetEntryCreateDto entryDto = new FieldSetEntryCreateDto(3L, 2);
        FieldConfiguration fieldConfig3 = new FieldConfiguration();
        fieldConfig3.setId(3L);
        fieldConfig3.setName("Field Config 3");
        fieldConfig3.setTenant(tenant);

        FieldSetEntry newEntry = new FieldSetEntry();
        newEntry.setId(3L);
        newEntry.setFieldSet(fieldSet);
        newEntry.setFieldConfiguration(fieldConfig3);
        newEntry.setOrderIndex(2);

        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));
        when(fieldConfigurationLookup.getById(3L, tenant)).thenReturn(fieldConfig3);
        when(fieldSetEntryRepository.save(any(FieldSetEntry.class))).thenReturn(newEntry);
        when(dtoMapper.toFieldSetEntryViewDto(newEntry)).thenReturn(new FieldSetEntryViewDto(3L, null, 2));

        // When
        FieldSetEntryViewDto result = fieldSetService.addEntry(tenant, 1L, entryDto);

        // Then
        assertNotNull(result);
        verify(fieldSetRepository).findByIdAndTenant(1L, tenant);
        verify(fieldConfigurationLookup).getById(3L, tenant);
        verify(fieldSetEntryRepository).save(any(FieldSetEntry.class));
    }

    @Test
    void testAddEntry_AlreadyPresent_ThrowsException() {
        // Given
        FieldSetEntryCreateDto entryDto = new FieldSetEntryCreateDto(1L, 2);
        when(fieldSetRepository.findByIdAndTenant(1L, tenant)).thenReturn(Optional.of(fieldSet));
        when(fieldConfigurationLookup.getById(1L, tenant)).thenReturn(fieldConfig1);

        // When & Then
        assertThrows(IllegalStateException.class, () -> fieldSetService.addEntry(tenant, 1L, entryDto));
    }

    @Test
    void testIsNotInAnyItemTypeSet_Success() {
        // Given
        when(itemTypeConfigurationLookup.isItemTypeConfigurationInAnyFieldSet(1L, tenant)).thenReturn(false);

        // When
        boolean result = fieldSetService.isNotInAnyItemTypeSet(1L, tenant);

        // Then
        assertTrue(result);
        verify(itemTypeConfigurationLookup).isItemTypeConfigurationInAnyFieldSet(1L, tenant);
    }

    @Test
    void testGetByFieldSetEntryId_Success() {
        // Given
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.of(entry1));

        // When
        FieldSet result = fieldSetService.getByFieldSetEntryId(tenant, 1L);

        // Then
        assertNotNull(result);
        assertEquals(fieldSet, result);
        verify(fieldSetEntryRepository).findById(1L);
    }

    @Test
    void testGetByFieldSetEntryId_NotFound_ThrowsException() {
        // Given
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.getByFieldSetEntryId(tenant, 1L));
    }

    @Test
    void testGetByFieldSetEntryId_WrongTenant_ThrowsException() {
        // Given
        Tenant otherTenant = new Tenant();
        otherTenant.setId(2L);
        fieldSet.setTenant(otherTenant);
        when(fieldSetEntryRepository.findById(1L)).thenReturn(Optional.of(entry1));

        // When & Then
        assertThrows(ApiException.class, () -> fieldSetService.getByFieldSetEntryId(tenant, 1L));
    }
}
