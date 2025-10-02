package com.example.demo.factory;

import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import org.springframework.stereotype.Component;

@Component
public class FieldSetEntryFactory {

    public FieldSetEntry create(FieldSet fieldSet,
                                FieldConfiguration fieldConfiguration,
                                int orderIndex) {
        FieldSetEntry entry = new FieldSetEntry();
        entry.setFieldSet(fieldSet);
        entry.setFieldConfiguration(fieldConfiguration);
        entry.setOrderIndex(orderIndex);
        return entry;
    }
}
