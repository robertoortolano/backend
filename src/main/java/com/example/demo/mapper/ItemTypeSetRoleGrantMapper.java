package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeSetRoleGrantDTO;
import com.example.demo.entity.ItemTypeSetRoleGrant;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ItemTypeSetRoleGrantMapper {
    
    public ItemTypeSetRoleGrantDTO toDTO(ItemTypeSetRoleGrant entity) {
        if (entity == null) {
            return null;
        }
        
        Set<Long> grantedUserIds = entity.getGrant() != null && entity.getGrant().getUsers() != null 
            ? entity.getGrant().getUsers().stream()
                .map(user -> user.getId())
                .collect(Collectors.toSet())
            : Set.of();
            
        Set<Long> grantedGroupIds = entity.getGrant() != null && entity.getGrant().getGroups() != null 
            ? entity.getGrant().getGroups().stream()
                .map(group -> group.getId())
                .collect(Collectors.toSet())
            : Set.of();
            
        Set<Long> negatedUserIds = entity.getGrant() != null && entity.getGrant().getNegatedUsers() != null 
            ? entity.getGrant().getNegatedUsers().stream()
                .map(user -> user.getId())
                .collect(Collectors.toSet())
            : Set.of();
            
        Set<Long> negatedGroupIds = entity.getGrant() != null && entity.getGrant().getNegatedGroups() != null 
            ? entity.getGrant().getNegatedGroups().stream()
                .map(group -> group.getId())
                .collect(Collectors.toSet())
            : Set.of();
        
        return ItemTypeSetRoleGrantDTO.builder()
                .id(entity.getId())
                .itemTypeSetRoleId(entity.getItemTypeSetRole() != null ? entity.getItemTypeSetRole().getId() : null)
                .grantId(entity.getGrant() != null ? entity.getGrant().getId() : null)
                .tenantId(entity.getTenant() != null ? entity.getTenant().getId() : null)
                .grantedUserIds(grantedUserIds)
                .grantedGroupIds(grantedGroupIds)
                .negatedUserIds(negatedUserIds)
                .negatedGroupIds(negatedGroupIds)
                .build();
    }
    
    public ItemTypeSetRoleGrant toEntity(ItemTypeSetRoleGrantDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return ItemTypeSetRoleGrant.builder()
                .id(dto.getId())
                .build();
    }
}
