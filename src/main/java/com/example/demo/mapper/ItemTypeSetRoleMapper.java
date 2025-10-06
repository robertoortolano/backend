package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.entity.ItemTypeSetRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ItemTypeSetRoleGrantMapper.class})
public interface ItemTypeSetRoleMapper {
    
    @Mapping(target = "itemTypeSetId", source = "itemTypeSet.id")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "grantId", source = "grant.id")
    @Mapping(target = "grantName", ignore = true)
    @Mapping(target = "roleTemplateId", source = "roleTemplate.id")
    @Mapping(target = "roleTemplateName", source = "roleTemplate.name")
    @Mapping(target = "grants", source = "grants")
    ItemTypeSetRoleDTO toDTO(ItemTypeSetRole entity);
    
    List<ItemTypeSetRoleDTO> toDTOList(List<ItemTypeSetRole> entities);
    
    @Mapping(target = "itemTypeSet", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "grant", ignore = true)
    @Mapping(target = "roleTemplate", ignore = true)
    @Mapping(target = "grants", ignore = true)
    ItemTypeSetRole toEntity(ItemTypeSetRoleDTO dto);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "itemTypeSet", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "grant", ignore = true)
    @Mapping(target = "roleTemplate", ignore = true)
    @Mapping(target = "grants", ignore = true)
    void updateEntity(ItemTypeSetRoleDTO dto, @MappingTarget ItemTypeSetRole entity);
}
