package com.example.demo.mapper;

import com.example.demo.dto.TransitionViewDto;
import com.example.demo.entity.Transition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransitionMapper {

    TransitionViewDto toViewDto(Transition transition);
}
