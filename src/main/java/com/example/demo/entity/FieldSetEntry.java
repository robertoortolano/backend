package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldSetEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fieldset_id")
    private FieldSet fieldSet;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fieldconfiguration_id")
    private FieldConfiguration fieldConfiguration;

    @Column(name = "order_index")
    private int orderIndex;

}

