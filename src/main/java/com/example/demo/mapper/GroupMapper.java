package com.example.demo.mapper;

import com.example.demo.dto.GroupCreateDto;
import com.example.demo.dto.GroupUpdateDto;
import com.example.demo.dto.GroupViewDto;
import com.example.demo.entity.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserResponseMapper.class})
public interface GroupMapper {
    
    GroupViewDto toViewDto(Group group);
    
    List<GroupViewDto> toViewDtos(List<Group> groups);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "deniedUsers", ignore = true)
    Group toEntity(GroupCreateDto dto);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "deniedUsers", ignore = true)
    void updateGroupFromDto(GroupUpdateDto dto, @MappingTarget Group group);
}

