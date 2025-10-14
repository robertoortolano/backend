package com.example.demo.mapper;

import com.example.demo.dto.TenantUserDto;
import com.example.demo.dto.UserAccessStatusDto;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TenantUserMapper {

    /**
     * Converte User a TenantUserDto
     * Nota: i ruoli devono essere passati separatamente dal service
     */
    @Mapping(target = "roles", ignore = true)
    TenantUserDto toTenantUserDto(User user);

    /**
     * Converte User a UserAccessStatusDto
     * Nota: hasAccess e roles devono essere settati dal service
     */
    @Mapping(target = "hasAccess", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(source = "id", target = "userId")
    UserAccessStatusDto toUserAccessStatusDto(User user);
}

