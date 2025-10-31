package com.example.demo.mapper;

import com.example.demo.dto.FieldTypeDescriptorDto;
import com.example.demo.enums.FieldType;
import com.example.demo.fieldtype.FieldTypeDescriptor;
import com.example.demo.fieldtype.FieldTypeRegistry;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class FieldTypeDescriptorMapper {

    @Autowired
    protected FieldTypeRegistry registry;

    /**
     * Mappa FieldTypeDescriptor a FieldTypeDescriptorDto usando MapStruct
     */
    public abstract FieldTypeDescriptorDto toDto(FieldTypeDescriptor descriptor);

    /**
     * Mappa FieldType a FieldTypeDescriptorDto tramite FieldTypeDescriptor
     * Usa MapStruct per mappare da FieldTypeDescriptor a DTO dopo aver ottenuto il descriptor
     */
    public FieldTypeDescriptorDto toDto(FieldType fieldType) {
        FieldTypeDescriptor desc = registry.getDescriptor(fieldType);
        if (desc == null) return null;
        return toDto(desc);
    }

    /**
     * Metodo helper per mappare da FieldTypeDescriptor (generato automaticamente da MapStruct)
     */
    @AfterMapping
    protected void afterMapping(FieldTypeDescriptor source, @MappingTarget FieldTypeDescriptorDto target) {
        // MapStruct mapperà automaticamente i campi con lo stesso nome
        // Questo metodo può essere usato per logica aggiuntiva se necessaria
    }
}




