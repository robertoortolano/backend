package com.example.demo.mapper;

import com.example.demo.dto.PermissionAssignmentDto;
import com.example.demo.entity.PermissionAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {RoleMapper.class, GrantMapper.class})
public interface PermissionAssignmentMapper {
    
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "grantId", source = "grant.id")
    @Mapping(target = "roleIds", expression = "java(entity.getRoles() != null ? entity.getRoles().stream().map(com.example.demo.entity.Role::getId).collect(java.util.stream.Collectors.toSet()) : null)")
    @Mapping(target = "roles", source = "roles")
    PermissionAssignmentDto toDto(PermissionAssignment entity);
    
    List<PermissionAssignmentDto> toDtoList(List<PermissionAssignment> entities);
    
}

