package com.example.demo.mapper;

import com.example.demo.dto.GrantViewDto;
import com.example.demo.entity.Grant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserResponseMapper.class, GroupMapper.class})
public interface GrantMapper {
    
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "defaultRole", source = "role.defaultRole")
    GrantViewDto toViewDto(Grant grant);
    
    List<GrantViewDto> toViewDtoList(List<Grant> grants);
    
}

