package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    private String status; // Es. "To Do", "In Progress", "Done"

    @ManyToOne
    @JoinColumn(name = "itemtype_id")
    private ItemType itemType;

    // Getters/Setters
}
