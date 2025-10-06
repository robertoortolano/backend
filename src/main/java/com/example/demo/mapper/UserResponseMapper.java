package com.example.demo.mapper;

import com.example.demo.dto.UserResponseDto;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface UserResponseMapper {
    
    UserResponseDto toDto(User user);
    
    List<UserResponseDto> toDtoList(List<User> users);
    
    Set<UserResponseDto> toDtoSet(Set<User> users);
}

