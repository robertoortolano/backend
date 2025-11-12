package com.example.demo.security;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Component("securityService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityServiceFacade {

    private final TenantSecurityService tenantSecurityService;
    private final ProjectSecurityService projectSecurityService;
    private final FieldConfigurationService fieldConfigurationService;
    private final FieldSetService fieldSetService;
    private final FieldSetLookup fieldSetLookup;
    private final WorkflowStatusService workflowStatusService;
    private final ItemTypeConfigurationService itemTypeConfigurationService;
    private final WorkflowService workflowService;
    private final WorkflowLookup workflowLookup;
    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final com.example.demo.repository.ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    // General
    public boolean hasAccessToGlobals(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return isTenantAdmin(user, tenant);
    }

    public boolean canAccessGlobalCatalog(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return hasTenantOrProjectAdmin(user, tenant);
    }

    // Field operations
    public boolean canCreateField(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return hasTenantOrProjectAdmin(user, tenant);
    }

    public boolean canDeleteField(Object principal, Tenant tenant, Long fieldId) {
        User user = extractUser(principal);

        return (
                !fieldConfigurationService.isFieldInAnyFieldConfiguration(tenant, fieldId) &&
                hasTenantOrProjectAdmin(user, tenant)
        );
    }

    public boolean canEditField(Object principal, Tenant tenant, Long fieldId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (isTenantAdmin(user, tenant)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il field è usato
        Set<Project> usedInProjects = fieldConfigurationService.getProjectsUsingField(tenant, fieldId);

        // Se il field non è usato in alcuna FieldConfiguration → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return hasAnyProjectAdmin(user, tenant);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il campo è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), "ADMIN")) {
                return false;
            }
        }

        return true;
    }



    // Field Configuration operations
    public boolean canCreateFieldConfiguration(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        boolean hasTenantAdmin = tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN");
        boolean hasProjectAdmin = projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, "ADMIN");
        
        return hasTenantAdmin || hasProjectAdmin;
    }

    public boolean canDeleteFieldConfiguration(Object principal, Tenant tenant, Long fieldConfigurationId) {
        User user = extractUser(principal);

        FieldConfiguration fieldConfiguration = fieldConfigurationLookup.getById(fieldConfigurationId, tenant);

        return !fieldConfigurationService.isFieldConfigurationInAnyFieldSet(fieldConfigurationId, tenant) &&
               (
                       tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                       (
                               fieldConfiguration.getProject() != null &&
                               projectSecurityService.hasProjectRole(user, tenant, fieldConfiguration.getProject().getId(), "ADMIN")
                       )
               );

    }

    public boolean canEditFieldConfiguration(Object principal, Tenant tenant, Long fieldConfigurationId) {
        User user = extractUser(principal);

        FieldConfiguration fieldConfiguration = fieldConfigurationLookup.getById(fieldConfigurationId, tenant);

        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
               (
                       fieldConfiguration.getProject() != null &&
                       projectSecurityService.hasProjectRole(user, tenant, fieldConfiguration.getProject().getId(), "ADMIN")
               );
    }


    // Field Set operations
    @SuppressWarnings("java:S4144")
    public boolean canCreateFieldSet(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return isTenantAdmin(user, tenant) ||
                (
                        projectId != null &&
                        isProjectAdmin(user, tenant, projectId)
                );
    }

    public boolean canDeleteFieldSet(Object principal, Tenant tenant, Long fieldSetId) {
        User user = extractUser(principal);

        FieldSet fieldSet = fieldSetLookup.getById(fieldSetId, tenant);

        return fieldSetService.isNotInAnyItemTypeSet(fieldSetId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                                (
                                        fieldSet.getProject() != null &&
                                        projectSecurityService.hasProjectRole(user, tenant, fieldSet.getProject().getId(), "ADMIN")
                                )
                );
    }

    public boolean canDeleteFieldSetEntry(Object principal, Tenant tenant, Long fieldSetEntryId) {
        FieldSet fieldSet = fieldSetService.getByFieldSetEntryId(tenant, fieldSetEntryId);

        if (!fieldSet.getTenant().equals(tenant)) {
            throw(new ApiException("Wrong tenant"));
        }

        return canEditFieldSet(principal, tenant, fieldSet.getId());
    }

    public boolean canEditFieldSet(Object principal, Tenant tenant, Long fieldSetId) {
        User user = extractUser(principal);

        FieldSet fieldSet = fieldSetLookup.getById(fieldSetId, tenant);

        return isTenantAdmin(user, tenant) ||
                (
                        fieldSet.getProject() != null &&
                        isProjectAdmin(user, tenant, fieldSet.getProject().getId())
                );
    }

    public boolean canViewFieldSet(Object principal, Tenant tenant, Long fieldSetId) {
        User user = extractUser(principal);
        FieldSet fieldSet = fieldSetLookup.getById(fieldSetId, tenant);

        if (fieldSet.getProject() == null) {
            return isTenantAdmin(user, tenant);
        }

        return isTenantAdmin(user, tenant) ||
                isProjectAdmin(user, tenant, fieldSet.getProject().getId());
    }


    // Item Type operations
    public boolean canCreateItemType(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return hasTenantOrProjectAdmin(user, tenant);
    }

    public boolean canDeleteItemType(Object principal, Tenant tenant, Long itemTypeId) {
        User user = extractUser(principal);

        return (
                !itemTypeConfigurationService.isItemTypeInAnyItemTypeConfiguration(tenant, itemTypeId) &&
                        hasTenantOrProjectAdmin(user, tenant)
        );
    }

    public boolean canEditItemType(Object principal, Tenant tenant, Long itemTypeId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (isTenantAdmin(user, tenant)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il itemType è usato
        Set<Project> usedInProjects = itemTypeConfigurationService.getProjectsUsingItemType(itemTypeId, tenant);

        // Se il itemType non è usato in alcuna itemTypeConfiguration → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return hasAnyProjectAdmin(user, tenant);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il itemType è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), "ADMIN")) {
                return false;
            }
        }

        return true;
    }


    // Status operations
    public boolean canCreateStatus(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return hasTenantOrProjectAdmin(user, tenant);
    }

    public boolean canDeleteStatus(Object principal, Tenant tenant, Long statusId) {
        User user = extractUser(principal);

        return (
                !workflowStatusService.isStatusUsedInAnyWorkflow(tenant, statusId) &&
                        hasTenantOrProjectAdmin(user, tenant)
        );
    }

    public boolean canEditStatus(Object principal, Tenant tenant, Long statusId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (isTenantAdmin(user, tenant)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il statusId è usato
        Set<Project> usedInProjects = workflowStatusService.findProjectsUsingStatus(statusId, tenant);

        // Se il statusId non è usato in alcuna workflow → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return hasAnyProjectAdmin(user, tenant);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il itemType è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), "ADMIN")) {
                return false;
            }
        }

        return true;
    }


    // Workflow operations
    @SuppressWarnings("java:S4144")
    public boolean canCreateWorkflow(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return isTenantAdmin(user, tenant) ||
                (projectId != null && isProjectAdmin(user, tenant, projectId));
    }

    public boolean canDeleteWorkflow(Object principal, Tenant tenant, Long workflowId) {
        User user = extractUser(principal);

        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);

        return workflowLookup.isNotInAnyItemTypeSet(workflowId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                                (
                                        workflow.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, workflow.getProject().getId(), "ADMIN")
                                )
                );
    }

    public boolean canEditWorkflow(Object principal, Tenant tenant, Long workflowId) {
        User user = extractUser(principal);

        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);

        return isTenantAdmin(user, tenant) ||
                (
                        workflow.getProject() != null &&
                                isProjectAdmin(user, tenant, workflow.getProject().getId())
                );
    }

    public boolean canViewItemTypeSetPermissions(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);
        if (isTenantAdmin(user, tenant)) {
            return true;
        }
        return projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, "ADMIN");
    }

    /**
     * Verifica se l'utente può accedere alla migrazione di una ItemTypeConfiguration.
     * - Tenant Admin: sempre accesso
     * - Project Admin: accesso solo se l'ItemTypeConfiguration appartiene a un ITS di progetto
     *   e l'utente è Project Admin di quel progetto
     */
    public boolean canAccessItemTypeConfigurationMigration(Object principal, Tenant tenant, Long itemTypeConfigurationId) {
        User user = extractUser(principal);
        
        // Tenant Admin ha sempre accesso
        if (isTenantAdmin(user, tenant)) {
            return true;
        }
        
        // Verifica se l'ItemTypeConfiguration appartiene a un ITS di progetto
        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(itemTypeConfigurationId)
                .orElse(null);
        
        if (config == null || !config.getTenant().getId().equals(tenant.getId())) {
            return false;
        }
        
        // Trova l'ItemTypeSet che contiene questa configurazione
        List<ItemTypeSet> itemTypeSets = itemTypeSetLookup.findByItemTypeConfigurationId(itemTypeConfigurationId, tenant);
        
        if (itemTypeSets.isEmpty()) {
            return false;
        }
        
        // Verifica se almeno un ItemTypeSet è di progetto (scope = PROJECT) e l'utente è Project Admin di quel progetto
        // NOTA: Gli ITS globali (scope = TENANT) possono essere modificati solo da Tenant Admin
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            // Verifica se l'ITS è di progetto (scope = PROJECT)
            if (itemTypeSet.getScope() == com.example.demo.enums.ScopeType.PROJECT && itemTypeSet.getProject() != null) {
                // ItemTypeSet di progetto: verifica se l'utente è Project Admin
                if (isProjectAdmin(user, tenant, itemTypeSet.getProject().getId())) {
                    return true;
                }
            }
            // NOTA: Gli ITS globali (scope = TENANT) possono essere modificati solo da Tenant Admin,
            // quindi se l'utente non è Tenant Admin, non ha accesso
        }
        
        return false;
    }

/*
    // Item Type Configuration operations
    public boolean canCreateItemTypeConfiguration(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, "ADMIN"));
    }

    public boolean canDeleteItemTypeConfiguration(Object principal, Tenant tenant, Long itemTypeConfigurationId) {
        User user = extractUser(principal);

        ItemTypeConfiguration itemTypeConfiguration = itemTypeConfigurationService.getById(itemTypeConfigurationId, tenant);

        return !itemTypeConfigurationService.isItemTypeConfigurationInAnyItemTypeSet(itemTypeConfigurationId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                                (
                                        itemTypeConfiguration.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, itemTypeConfiguration.getProject().getId(), "ADMIN")
                                )
                );
    }

    public boolean canEditItemTypeConfiguration(Object principal, Tenant tenant, Long itemTypeConfigurationId) {
        User user = extractUser(principal);

        ItemTypeConfiguration itemTypeConfiguration = itemTypeConfigurationService.getById(itemTypeConfigurationId, tenant);

        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                (
                        itemTypeConfiguration.getProject() != null &&
                                projectSecurityService.hasProjectRole(user, tenant, itemTypeConfiguration.getProject().getId(), "ADMIN")
                );
    }
*/
/*
    // Item Type Set operations
    public boolean canCreateItemTypeSet(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, "ADMIN"));
    }

    public boolean canDeleteItemTypeSet(Object principal, Tenant tenant, Long itemTypeSetId) {
        User user = extractUser(principal);

        ItemTypeSet itemTypeSet = itemTypeSetService.getById(tenant, itemTypeSetId);

        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN") ||
                (
                        itemTypeSet.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, workflow.getProject().getId(), "ADMIN")
                                )
                );
    }

    public boolean canEditItemItemTypeSet(User user, ItemTypeSet itemTypeSet) {
        return true;
    }


    // Project Item Type Set assignment
    public boolean canAssignItemTypeSetToProject(User user, Project project, ItemTypeSet itemTypeSet) {
        return true;
    }
*/

    // Private
    private User extractUser(Object principal) {
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getUser();
        }
        throw new ApiException("Unexpected principal type");
    }

    private boolean isTenantAdmin(User user, Tenant tenant) {
        return tenantSecurityService.hasTenantRoleName(user, tenant, "ADMIN");
    }

    private boolean hasAnyProjectAdmin(User user, Tenant tenant) {
        return projectSecurityService.hasRoleNameInAnyProject(user, tenant, "ADMIN");
    }

    private boolean hasTenantOrProjectAdmin(User user, Tenant tenant) {
        return isTenantAdmin(user, tenant) || hasAnyProjectAdmin(user, tenant);
    }

    private boolean isProjectAdmin(User user, Tenant tenant, Long projectId) {
        if (projectId == null) {
            return false;
        }
        return projectSecurityService.hasProjectRole(user, tenant, projectId, "ADMIN");
    }


}
