package com.example.demo.security;

import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import com.example.demo.exception.ApiException;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    // General
    public boolean hasAccessToGlobals(Object principal, Tenant tenant) {
        User user = extractUser(principal);
        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN);
    }

    // Field operations
    public boolean canCreateField(Object principal, Tenant tenant) {
        return hasAccessToGlobals(principal, tenant);
    }

    public boolean canDeleteField(Object principal, Tenant tenant, Long fieldId) {
        User user = extractUser(principal);

        return (
                !fieldConfigurationService.isFieldInAnyFieldConfiguration(tenant, fieldId) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                        projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN)
                )
        );
    }

    public boolean canEditField(Object principal, Tenant tenant, Long fieldId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il field è usato
        Set<Project> usedInProjects = fieldConfigurationService.getProjectsUsingField(tenant, fieldId);

        // Se il field non è usato in alcuna FieldConfiguration → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il campo è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), RoleName.ADMIN)) {
                return false;
            }
        }

        return true;
    }



    // Field Configuration operations
    public boolean canCreateFieldConfiguration(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
               (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, RoleName.ADMIN));
    }

    public boolean canDeleteFieldConfiguration(Object principal, Tenant tenant, Long fieldConfigurationId) {
        User user = extractUser(principal);

        FieldConfiguration fieldConfiguration = fieldConfigurationLookup.getById(fieldConfigurationId, tenant);

        return !fieldConfigurationService.isFieldConfigurationInAnyFieldSet(fieldConfigurationId, tenant) &&
               (
                       tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                       (
                               fieldConfiguration.getProject() != null &&
                               projectSecurityService.hasProjectRole(user, tenant, fieldConfiguration.getProject().getId(), RoleName.ADMIN)
                       )
               );

    }

    public boolean canEditFieldConfiguration(Object principal, Tenant tenant, Long fieldConfigurationId) {
        User user = extractUser(principal);

        FieldConfiguration fieldConfiguration = fieldConfigurationLookup.getById(fieldConfigurationId, tenant);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
               (
                       fieldConfiguration.getProject() != null &&
                       projectSecurityService.hasProjectRole(user, tenant, fieldConfiguration.getProject().getId(), RoleName.ADMIN)
               );
    }


    // Field Set operations
    @SuppressWarnings("java:S4144")
    public boolean canCreateFieldSet(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (
                        projectId != null &&
                        projectSecurityService.hasProjectRole(user, tenant, projectId, RoleName.ADMIN)
                );
    }

    public boolean canDeleteFieldSet(Object principal, Tenant tenant, Long fieldSetId) {
        User user = extractUser(principal);

        FieldSet fieldSet = fieldSetLookup.getById(fieldSetId, tenant);

        return fieldSetService.isNotInAnyItemTypeSet(fieldSetId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                                (
                                        fieldSet.getProject() != null &&
                                        projectSecurityService.hasProjectRole(user, tenant, fieldSet.getProject().getId(), RoleName.ADMIN)
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

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (
                        fieldSet.getProject() != null &&
                        projectSecurityService.hasProjectRole(user, tenant, fieldSet.getProject().getId(), RoleName.ADMIN)
                );
    }


    // Item Type operations
    public boolean canCreateItemType(Object principal, Tenant tenant) {
        return hasAccessToGlobals(principal, tenant);
    }

    public boolean canDeleteItemType(Object principal, Tenant tenant, Long itemTypeId) {
        User user = extractUser(principal);

        return (
                !itemTypeConfigurationService.isItemTypeInAnyItemTypeConfiguration(tenant, itemTypeId) &&
                        (
                                tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                                projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN)
                        )
        );
    }

    public boolean canEditItemType(Object principal, Tenant tenant, Long itemTypeId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il itemType è usato
        Set<Project> usedInProjects = itemTypeConfigurationService.getProjectsUsingItemType(itemTypeId, tenant);

        // Se il itemType non è usato in alcuna itemTypeConfiguration → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il itemType è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), RoleName.ADMIN)) {
                return false;
            }
        }

        return true;
    }


    // Status operations
    public boolean canCreateStatus(Object principal, Tenant tenant) {
        return hasAccessToGlobals(principal, tenant);
    }

    public boolean canDeleteStatus(Object principal, Tenant tenant, Long statusId) {
        User user = extractUser(principal);

        return (
                !workflowStatusService.isStatusUsedInAnyWorkflow(tenant, statusId) &&
                        (
                                tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                                        projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN)
                        )
        );
    }

    public boolean canEditStatus(Object principal, Tenant tenant, Long statusId) {
        User user = extractUser(principal);

        // Se è tenant admin, ha sempre diritto
        if (tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN)) {
            return true;
        }

        // Altrimenti deve essere project admin di ogni progetto in cui il statusId è usato
        Set<Project> usedInProjects = workflowStatusService.findProjectsUsingStatus(statusId, tenant);

        // Se il statusId non è usato in alcuna workflow → ok se è almeno project admin da qualche parte
        if (usedInProjects.isEmpty()) {
            return projectSecurityService.hasRoleNameInAnyProject(user, tenant, RoleName.ADMIN);
        }

        // Verifica che l’utente sia ADMIN in tutti i progetti dove il itemType è usato
        for (Project project : usedInProjects) {
            if (!project.getTenant().equals(tenant)) {
                throw new SecurityException("Tenant security exception");
            }
            if (!projectSecurityService.hasProjectRole(user, tenant, project.getId(), RoleName.ADMIN)) {
                return false;
            }
        }

        return true;
    }


    // Workflow operations
    @SuppressWarnings("java:S4144")
    public boolean canCreateWorkflow(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, RoleName.ADMIN));
    }

    public boolean canDeleteWorkflow(Object principal, Tenant tenant, Long workflowId) {
        User user = extractUser(principal);

        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);

        return workflowLookup.isNotInAnyItemTypeSet(workflowId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                                (
                                        workflow.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, workflow.getProject().getId(), RoleName.ADMIN)
                                )
                );
    }

    public boolean canEditItemWorkflow(Object principal, Tenant tenant, Long workflowId) {
        User user = extractUser(principal);

        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (
                        workflow.getProject() != null &&
                                projectSecurityService.hasProjectRole(user, tenant, workflow.getProject().getId(), RoleName.ADMIN)
                );
    }

/*
    // Item Type Configuration operations
    public boolean canCreateItemTypeConfiguration(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, RoleName.ADMIN));
    }

    public boolean canDeleteItemTypeConfiguration(Object principal, Tenant tenant, Long itemTypeConfigurationId) {
        User user = extractUser(principal);

        ItemTypeConfiguration itemTypeConfiguration = itemTypeConfigurationService.getById(itemTypeConfigurationId, tenant);

        return !itemTypeConfigurationService.isItemTypeConfigurationInAnyItemTypeSet(itemTypeConfigurationId, tenant) &&
                (
                        tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                                (
                                        itemTypeConfiguration.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, itemTypeConfiguration.getProject().getId(), RoleName.ADMIN)
                                )
                );
    }

    public boolean canEditItemTypeConfiguration(Object principal, Tenant tenant, Long itemTypeConfigurationId) {
        User user = extractUser(principal);

        ItemTypeConfiguration itemTypeConfiguration = itemTypeConfigurationService.getById(itemTypeConfigurationId, tenant);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (
                        itemTypeConfiguration.getProject() != null &&
                                projectSecurityService.hasProjectRole(user, tenant, itemTypeConfiguration.getProject().getId(), RoleName.ADMIN)
                );
    }
*/
/*
    // Item Type Set operations
    public boolean canCreateItemTypeSet(Object principal, Tenant tenant, Long projectId) {
        User user = extractUser(principal);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (projectId != null && projectSecurityService.hasProjectRole(user, tenant, projectId, RoleName.ADMIN));
    }

    public boolean canDeleteItemTypeSet(Object principal, Tenant tenant, Long itemTypeSetId) {
        User user = extractUser(principal);

        ItemTypeSet itemTypeSet = itemTypeSetService.getById(tenant, itemTypeSetId);

        return tenantSecurityService.hasTenantRoleName(user, tenant, RoleName.ADMIN) ||
                (
                        itemTypeSet.getProject() != null &&
                                                projectSecurityService.hasProjectRole(user, tenant, workflow.getProject().getId(), RoleName.ADMIN)
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
        throw new IllegalArgumentException("Unexpected principal type");
    }


}
