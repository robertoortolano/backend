package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse (
    String message,
    Integer status
) {

    public static ApiResponse ok(String message) {
        return new ApiResponse(message, HttpStatus.OK.value());
    }

    public static ApiResponse error(String message, HttpStatus status) {
        return new ApiResponse(message, status.value());
    }

}
