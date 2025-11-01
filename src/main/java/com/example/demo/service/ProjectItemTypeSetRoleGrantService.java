package com.example.demo.service;

import com.example.demo.dto.ItemTypeSetRoleGrantCreateDto;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectItemTypeSetRoleGrantService {
    
    private final ProjectItemTypeSetRoleGrantRepository projectItemTypeSetRoleGrantRepository;
    private final ItemTypeSetRoleRepository itemTypeSetRoleRepository;
    private final GrantRepository grantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final DtoMapperFacade dtoMapper;
    
    /**
     * Crea o aggiorna un Grant per progetto assegnato a un ItemTypeSetRole.
     * Il Grant viene creato al momento dell'assegnazione con gli utenti/gruppi specificati.
     */
    public void createOrUpdateProjectGrant(ItemTypeSetRoleGrantCreateDto dto, Long projectId, Tenant tenant) {
        // Verifica che l'ItemTypeSetRole esista
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(dto.itemTypeSetRoleId(), tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSetRole not found"));
        
        // Verifica che il progetto esista
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica se esiste già una grant per questo progetto
        Optional<ProjectItemTypeSetRoleGrant> existingOpt = projectItemTypeSetRoleGrantRepository
                .findByItemTypeSetRoleAndProjectAndTenant(role, project, tenant);
        
        Grant grant;
        if (existingOpt.isPresent()) {
            // Aggiorna Grant esistente
            grant = existingOpt.get().getGrant();
            grant = grantRepository.findByIdWithCollections(grant.getId())
                    .orElseThrow(() -> new ApiException("Grant not found"));
            
            // Pulisci utenti e gruppi esistenti
            grant.getUsers().clear();
            grant.getGroups().clear();
            grant.getNegatedUsers().clear();
            grant.getNegatedGroups().clear();
        } else {
            // Crea nuovo Grant
            grant = new Grant();
            grant.setRole(null);
        }
        
        // Popola il Grant con utenti e gruppi
        if (dto.userIds() != null && !dto.userIds().isEmpty()) {
            Set<User> users = new HashSet<>();
            for (Long userId : dto.userIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                users.add(user);
            }
            grant.setUsers(users);
        }
        
        if (dto.groupIds() != null && !dto.groupIds().isEmpty()) {
            Set<Group> groups = new HashSet<>();
            for (Long groupId : dto.groupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Group not found: " + groupId));
                groups.add(group);
            }
            grant.setGroups(groups);
        }
        
        if (dto.negatedUserIds() != null && !dto.negatedUserIds().isEmpty()) {
            Set<User> negatedUsers = new HashSet<>();
            for (Long userId : dto.negatedUserIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("Negated user not found: " + userId));
                negatedUsers.add(user);
            }
            grant.setNegatedUsers(negatedUsers);
        }
        
        if (dto.negatedGroupIds() != null && !dto.negatedGroupIds().isEmpty()) {
            Set<Group> negatedGroups = new HashSet<>();
            for (Long groupId : dto.negatedGroupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Negated group not found: " + groupId));
                negatedGroups.add(group);
            }
            grant.setNegatedGroups(negatedGroups);
        }
        
        // Salva il Grant
        Grant savedGrant = grantRepository.save(grant);
        
        // Crea o aggiorna ProjectItemTypeSetRoleGrant
        if (existingOpt.isPresent()) {
            ProjectItemTypeSetRoleGrant existing = existingOpt.get();
            existing.setGrant(savedGrant);
            projectItemTypeSetRoleGrantRepository.save(existing);
        } else {
            ProjectItemTypeSetRoleGrant projectGrant = ProjectItemTypeSetRoleGrant.builder()
                    .itemTypeSetRole(role)
                    .grant(savedGrant)
                    .project(project)
                    .tenant(tenant)
                    .build();
            projectItemTypeSetRoleGrantRepository.save(projectGrant);
        }
    }
    
    /**
     * Recupera i dettagli di un Grant di progetto assegnato a un ItemTypeSetRole
     */
    public com.example.demo.dto.GrantDetailsDto getProjectGrantDetails(Long itemTypeSetRoleId, Long projectId, Tenant tenant) {
        ProjectItemTypeSetRoleGrant projectGrant = projectItemTypeSetRoleGrantRepository
                .findByItemTypeSetRoleIdAndProjectIdAndTenantId(itemTypeSetRoleId, projectId, tenant.getId())
                .orElseThrow(() -> new ApiException("Project grant not found"));
        
        Grant grant = grantRepository.findByIdWithCollections(projectGrant.getGrant().getId())
                .orElseThrow(() -> new ApiException("Grant not found"));
        
        return new com.example.demo.dto.GrantDetailsDto(
            grant.getId(),
            grant.getUsers().stream().map(dtoMapper::toUserSimpleDto).toList(),
            grant.getGroups().stream().map(dtoMapper::toGroupSimpleDto).toList(),
            grant.getNegatedUsers().stream().map(dtoMapper::toUserSimpleDto).toList(),
            grant.getNegatedGroups().stream().map(dtoMapper::toGroupSimpleDto).toList()
        );
    }
    
    /**
     * Rimuove una Grant di progetto assegnata a un ItemTypeSetRole
     */
    public void removeProjectGrant(Long itemTypeSetRoleId, Long projectId, Tenant tenant) {
        ProjectItemTypeSetRoleGrant projectGrant = projectItemTypeSetRoleGrantRepository
                .findByItemTypeSetRoleIdAndProjectIdAndTenantId(itemTypeSetRoleId, projectId, tenant.getId())
                .orElseThrow(() -> new ApiException("Project grant not found"));
        
        projectItemTypeSetRoleGrantRepository.delete(projectGrant);
        
        // Opzionale: rimuovi anche il Grant se non è usato altrove
        // Potresti voler mantenere il Grant per audit o per riutilizzo futuro
    }
    
    /**
     * Verifica se esiste una Grant di progetto per un ItemTypeSetRole
     */
    public boolean hasProjectGrant(Long itemTypeSetRoleId, Long projectId, Long tenantId) {
        return projectItemTypeSetRoleGrantRepository.existsByItemTypeSetRoleIdAndProjectIdAndTenantId(
                itemTypeSetRoleId, projectId, tenantId);
    }
}
