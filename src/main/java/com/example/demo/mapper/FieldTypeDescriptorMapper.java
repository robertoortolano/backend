package com.example.demo.mapper;

import com.example.demo.dto.FieldTypeDescriptorDto;
import com.example.demo.enums.FieldType;
import com.example.demo.fieldtype.FieldTypeDescriptor;
import com.example.demo.fieldtype.FieldTypeRegistry;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class FieldTypeDescriptorMapper {

    @Autowired
    protected FieldTypeRegistry registry;

    public FieldTypeDescriptorDto toDto(FieldType fieldType) {
        FieldTypeDescriptor desc = registry.getDescriptor(fieldType);
        if (desc == null) return null;

        FieldTypeDescriptorDto dto = new FieldTypeDescriptorDto();
        dto.setDisplayName(desc.getDisplayName());
        dto.setSupportsOptions(desc.isSupportsOptions());
        dto.setSupportsMultiple(desc.isSupportsMultiple());
        dto.setFrontendComponent(desc.getFrontendComponent());
        dto.setExtraConfig(desc.getExtraConfig());
        return dto;
    }
}




