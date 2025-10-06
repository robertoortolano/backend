package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeSetRoleGrantDTO;
import com.example.demo.entity.ItemTypeSetRoleGrant;
import com.example.demo.entity.User;
import com.example.demo.entity.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ItemTypeSetRoleGrantMapper {
    
    @Mapping(target = "itemTypeSetRoleId", source = "itemTypeSetRole.id")
    @Mapping(target = "grantId", source = "grant.id")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "grantedUserIds", expression = "java(mapUsersToIds(entity.getGrantedUsers()))")
    @Mapping(target = "grantedGroupIds", expression = "java(mapGroupsToIds(entity.getGrantedGroups()))")
    @Mapping(target = "negatedUserIds", expression = "java(mapUsersToIds(entity.getNegatedUsers()))")
    @Mapping(target = "negatedGroupIds", expression = "java(mapGroupsToIds(entity.getNegatedGroups()))")
    ItemTypeSetRoleGrantDTO toDTO(ItemTypeSetRoleGrant entity);
    
    List<ItemTypeSetRoleGrantDTO> toDTOList(List<ItemTypeSetRoleGrant> entities);
    
    Set<ItemTypeSetRoleGrantDTO> toDTOSet(Set<ItemTypeSetRoleGrant> entities);
    
    @Mapping(target = "itemTypeSetRole", ignore = true)
    @Mapping(target = "grant", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    ItemTypeSetRoleGrant toEntity(ItemTypeSetRoleGrantDTO dto);
    
    default Set<Long> mapUsersToIds(Set<User> users) {
        if (users == null) {
            return Set.of();
        }
        return users.stream()
                .map(User::getId)
                .collect(Collectors.toSet());
    }
    
    default Set<Long> mapGroupsToIds(Set<Group> groups) {
        if (groups == null) {
            return Set.of();
        }
        return groups.stream()
                .map(Group::getId)
                .collect(Collectors.toSet());
    }
}

