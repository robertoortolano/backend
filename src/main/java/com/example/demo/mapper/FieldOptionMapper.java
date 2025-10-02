package com.example.demo.mapper;

import com.example.demo.dto.FieldOptionCreateDto;
import com.example.demo.dto.FieldOptionUpdateDto;
import com.example.demo.dto.FieldOptionViewDto;
import com.example.demo.entity.FieldOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface FieldOptionMapper {

    // ========== VIEW ==========
    FieldOptionViewDto toViewDto(FieldOption option);
    Set<FieldOptionViewDto> toViewDtoSet(Set<FieldOption> options);

    // ========== CREATE ==========
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", constant = "true") // abilitate di default
    @Mapping(target = "orderIndex", constant = "0") // opzionale: se serve un default
    FieldOption toEntity(FieldOptionCreateDto dto);

    default Set<FieldOption> toEntitySetFromCreate(Set<FieldOptionCreateDto> dtos) {
        if (dtos == null) return Collections.emptySet();
        return dtos.stream()
                .map(this::toEntity)
                .collect(Collectors.toSet());
    }

    // ========== UPDATE ==========
    FieldOption toEntity(FieldOptionUpdateDto dto);

    Set<FieldOption> toEntitySetFromUpdate(Set<FieldOptionUpdateDto> dtos);

}

