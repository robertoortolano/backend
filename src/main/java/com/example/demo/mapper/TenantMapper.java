package com.example.demo.mapper;

import com.example.demo.dto.TenantDTO;
import com.example.demo.entity.Tenant;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantDTO toDto(Tenant tenant);

    List<TenantDTO> toDtoList(List<Tenant> tenants);
}









