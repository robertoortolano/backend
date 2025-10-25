package com.example.demo.service;

import com.example.demo.dto.ItemTypeCreateDto;
import com.example.demo.dto.ItemTypeDetailDto;
import com.example.demo.dto.ItemTypeViewDto;
import com.example.demo.entity.ItemType;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.ItemTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ItemTypeService {

    private final ItemTypeRepository itemTypeRepository;

    private final ItemTypeLookup itemTypeLookup;

    private final DtoMapperFacade dtoMapper;
    
    private final ItemTypeConfigurationService itemTypeConfigurationService;
    
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;


    @Transactional(readOnly = true)
    public List<ItemTypeViewDto> getAllForTenant(Tenant tenant) {
        return itemTypeRepository.findByTenant(tenant)
                .stream()
                .map(dtoMapper::toItemTypeDTO)
                .toList();
    }


    public ItemTypeViewDto createItemType(Tenant tenant, ItemTypeCreateDto dto) {
        boolean exists = itemTypeRepository.existsByNameAndTenant(dto.name(), tenant);
        if (exists) {
            throw new ApiException("Esiste già un ItemType con questo nome");
        }

        ItemType entity = dtoMapper.toItemType(dto);
        entity.setTenant(tenant);

        ItemType saved = itemTypeRepository.save(entity);
        return dtoMapper.toItemTypeDTO(saved);
    }

    public void saveAll(List<ItemType> itemTypeList) {
        if (itemTypeList.stream()
                .noneMatch(ItemType::isDefaultItemType)) {
            throw new ApiException("Default Item Type cannot be saved");
        }

        itemTypeRepository.saveAll(itemTypeList);
    }


    @Transactional(readOnly = true)
    public ItemTypeViewDto getById(Tenant tenant, Long itemTypeId) {
        return dtoMapper.toItemTypeDTO(itemTypeLookup.getById(tenant, itemTypeId));
    }


    // Aggiorna un item type esistente
    public ItemTypeViewDto updateItemType(Tenant tenant, Long itemTypeId, ItemTypeCreateDto dto) {

        ItemType itemType = itemTypeRepository.findByIdAndTenant(itemTypeId, tenant)
                .orElseThrow(() -> new ApiException("Item type non trovato"));

        if (itemType.isDefaultItemType()) {
            throw new ApiException("Default Item Type cannot be edited");
        }

        itemTypeRepository.findByNameAndTenant(dto.name(), tenant)
                .filter(found -> !found.getId().equals(itemTypeId))
                .ifPresent(found -> {
                    throw new ApiException("Esiste già un item type con questo nome per la tenant.");
                });

        dtoMapper.updateItemTypeFromDto(dto, itemType);

        return dtoMapper.toItemTypeDTO(itemTypeRepository.save(itemType));
    }

    public void deleteItemType(Tenant tenant, Long itemTypeId) {
        ItemType itemType = itemTypeRepository.findByIdAndTenant(itemTypeId, tenant)
                .orElseThrow(() -> new ApiException("Item Type not found"));

        if (itemType.isDefaultItemType()) {
            throw new ApiException("Default Item Type cannot be deleted");
        }
        
        if (itemTypeConfigurationService.isItemTypeInAnyItemTypeConfiguration(tenant, itemTypeId)) {
            throw new ApiException("Item Type is used in an ItemTypeSet and cannot be deleted");
        }

        itemTypeRepository.deleteByIdAndTenant(itemTypeId, tenant);
    }

    @Transactional(readOnly = true)
    public ItemTypeDetailDto getItemTypeDetail(Long itemTypeId, Tenant tenant) {
        ItemType itemType = itemTypeRepository.findByIdAndTenant(itemTypeId, tenant)
                .orElseThrow(() -> new ApiException("Item Type not found"));

        List<ItemTypeConfiguration> configs = itemTypeConfigurationLookup.getAllByItemType(itemTypeId, tenant);

        return dtoMapper.toItemTypeDetailDto(itemType, configs);
    }


}

