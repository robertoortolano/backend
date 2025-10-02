package com.example.demo.initializer;

import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;

public interface ProjectInitializer {
    void initialize(Project project, Tenant tenant);
}