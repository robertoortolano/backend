package com.example.demo.service;

import com.example.demo.dto.FieldTypeDescriptorDto;
import com.example.demo.enums.FieldType;
import com.example.demo.fieldtype.FieldTypeRegistry;
import com.example.demo.mapper.DtoMapperFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldTypeService {

    private final FieldTypeRegistry registry;
    private final DtoMapperFacade dtoMapper;

    public Map<String, FieldTypeDescriptorDto> getAllFieldTypes() {
        return registry.getAll().keySet().stream()
                .collect(Collectors.toMap(
                        FieldType::name,
                        dtoMapper::toFieldTypeDescriptorDto
                ));
    }
}

