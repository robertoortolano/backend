package com.example.demo.mapper;

import com.example.demo.dto.UserSimpleDto;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserSimpleMapper {
    
    UserSimpleDto toDto(User user);
    
    List<UserSimpleDto> toDtoList(List<User> users);
}

