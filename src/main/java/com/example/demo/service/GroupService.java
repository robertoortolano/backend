package com.example.demo.service;

import com.example.demo.dto.GroupCreateDto;
import com.example.demo.dto.GroupUpdateDto;
import com.example.demo.dto.GroupViewDto;
import com.example.demo.entity.Group;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.GroupRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final DtoMapperFacade dtoMapper;

    @Transactional(readOnly = true)
    public List<GroupViewDto> getAllForTenant(Tenant tenant) {
        List<Group> groups = groupRepository.findByTenant(tenant);
        return dtoMapper.toGroupViewDtos(groups);
    }

    @Transactional(readOnly = true)
    public GroupViewDto getById(Long id, Tenant tenant) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ApiException("Group not found"));
        
        if (!group.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Group does not belong to this tenant");
        }
        
        return dtoMapper.toGroupViewDto(group);
    }

    public GroupViewDto createGroup(Tenant tenant, GroupCreateDto dto) {
        // Verifica se esiste già un gruppo con lo stesso nome per questo tenant
        if (groupRepository.existsByNameAndTenant(dto.name(), tenant)) {
            throw new ApiException("Esiste già un gruppo con questo nome");
        }

        Group group = dtoMapper.toGroup(dto);
        group.setTenant(tenant);
        
        // Aggiungi gli utenti se specificati
        if (dto.userIds() != null && !dto.userIds().isEmpty()) {
            Set<User> users = new HashSet<>();
            for (Long userId : dto.userIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                users.add(user);
            }
            group.setUsers(users);
        }

        Group saved = groupRepository.save(group);
        return dtoMapper.toGroupViewDto(saved);
    }

    public GroupViewDto updateGroup(Long id, Tenant tenant, GroupUpdateDto dto) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ApiException("Group not found"));
        
        if (!group.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Group does not belong to this tenant");
        }

        // Verifica se esiste già un altro gruppo con lo stesso nome
        if (!group.getName().equals(dto.name()) && 
            groupRepository.existsByNameAndTenant(dto.name(), tenant)) {
            throw new ApiException("Esiste già un gruppo con questo nome");
        }

        dtoMapper.updateGroupFromDto(dto, group);
        
        // Aggiorna gli utenti
        if (dto.userIds() != null) {
            Set<User> users = new HashSet<>();
            for (Long userId : dto.userIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                users.add(user);
            }
            group.setUsers(users);
        } else {
            group.getUsers().clear();
        }

        Group saved = groupRepository.save(group);
        return dtoMapper.toGroupViewDto(saved);
    }

    public void deleteGroup(Long id, Tenant tenant) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ApiException("Group not found"));
        
        if (!group.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Group does not belong to this tenant");
        }

        groupRepository.delete(group);
    }
}



