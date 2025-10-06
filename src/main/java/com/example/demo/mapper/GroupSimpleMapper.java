package com.example.demo.mapper;

import com.example.demo.dto.GroupSimpleDto;
import com.example.demo.entity.Group;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupSimpleMapper {
    
    GroupSimpleDto toDto(Group group);
    
    List<GroupSimpleDto> toDtoList(List<Group> groups);
}

