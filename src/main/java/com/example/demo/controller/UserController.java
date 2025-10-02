package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @PostMapping
    public User create(@RequestBody User user) {
        return userRepository.save(user);
    }
}
