package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleGrantDTO;
import com.example.demo.entity.ItemTypeSetRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ItemTypeSetRoleMapper {
    
    @Autowired
    private ItemTypeSetRoleGrantMapper itemTypeSetRoleGrantMapper;
    
    public ItemTypeSetRoleDTO toDTO(ItemTypeSetRole entity) {
        if (entity == null) {
            return null;
        }
        
        Set<ItemTypeSetRoleGrantDTO> grantDTOs = entity.getGrants() != null 
            ? entity.getGrants().stream()
                .map(itemTypeSetRoleGrantMapper::toDTO)
                .collect(Collectors.toSet())
            : Set.of();
        
        return ItemTypeSetRoleDTO.builder()
                .id(entity.getId())
                .roleType(entity.getRoleType())
                .name(entity.getName())
                .description(entity.getDescription())
                .itemTypeSetId(entity.getItemTypeSet() != null ? entity.getItemTypeSet().getId() : null)
                .relatedEntityType(entity.getRelatedEntityType())
                .relatedEntityId(entity.getRelatedEntityId())
                .secondaryEntityType(entity.getSecondaryEntityType())
                .secondaryEntityId(entity.getSecondaryEntityId())
                .tenantId(entity.getTenant() != null ? entity.getTenant().getId() : null)
                .grantId(entity.getGrant() != null ? entity.getGrant().getId() : null)
                //.grantName(entity.getGrant() != null ? entity.getGrant().getName() : null)
                .roleTemplateId(entity.getRoleTemplate() != null ? entity.getRoleTemplate().getId() : null)
                .roleTemplateName(entity.getRoleTemplate() != null ? entity.getRoleTemplate().getName().toString() : null)
                .assignmentType(entity.getAssignmentType())
                .grants(grantDTOs)
                .build();
    }
    
    public ItemTypeSetRole toEntity(ItemTypeSetRoleDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return ItemTypeSetRole.builder()
                .id(dto.getId())
                .roleType(dto.getRoleType())
                .name(dto.getName())
                .description(dto.getDescription())
                .relatedEntityType(dto.getRelatedEntityType())
                .relatedEntityId(dto.getRelatedEntityId())
                .secondaryEntityType(dto.getSecondaryEntityType())
                .secondaryEntityId(dto.getSecondaryEntityId())
                .build();
    }
}
