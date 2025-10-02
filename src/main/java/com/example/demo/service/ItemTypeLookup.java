package com.example.demo.service;

import com.example.demo.entity.ItemType;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeLookup {

    private final ItemTypeRepository itemTypeRepository;

    public ItemType getById(Tenant tenant, Long itemTypeId) {
        return itemTypeRepository.findByIdAndTenant(itemTypeId, tenant)
                .orElseThrow(() -> new ApiException("ItemType non trovato"));
    }

}
