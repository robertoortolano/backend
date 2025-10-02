package com.example.demo.repository;

import com.example.demo.entity.FieldOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FieldOptionRepository extends JpaRepository<FieldOption, Long> {

}

