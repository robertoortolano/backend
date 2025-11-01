package com.example.demo.mapper;

import com.example.demo.dto.RoleViewDto;
import com.example.demo.entity.Role;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    RoleViewDto toViewDto(Role role);

    List<RoleViewDto> toViewDtoList(List<Role> roles);
}

























