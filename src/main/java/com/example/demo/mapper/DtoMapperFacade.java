package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.FieldRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DtoMapperFacade {

    private final ProjectMapper projectMapper;
    private final ProjectSummaryMapper projectSummaryMapper;
    private final ItemTypeMapper itemTypeMapper;
    private final StatusMapper statusMapper;
    private final TransitionMapper transitionMapper;
    private final ItemTypeSetMapper itemTypeSetMapper;
    private final FieldMapper fieldMapper;
    private final FieldOptionMapper fieldOptionMapper;
    private final FieldSetMapper fieldSetMapper;
    private final FieldSetEntryMapper fieldSetEntryMapper;
    private final FieldConfigurationMapper fieldConfigurationMapper;
    private final FieldTypeDescriptorMapper fieldTypeDescriptorMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper workflowStatusMapper;

    // ---------------------
    // Project
    // ---------------------

    public ProjectViewDto toProjectViewDto(Project project) {
        return projectMapper.toViewDto(project);
    }

    public List<ProjectViewDto> toProjectViewDtos(List<Project> projects) {
        return projectMapper.toViewDtos(projects);
    }

    public Project toProject(ProjectCreateDto dto) {
        return projectMapper.toProject(dto);
    }

    public void updateProjectFromDto(ProjectUpdateDto dto, Project project) {
        projectMapper.updateProjectFromDto(dto, project);
    }

    // ---------------------
    // Project summary
    // ---------------------

    public ProjectSummaryDto toProjectSummaryDto(Project project) {
        return projectSummaryMapper.toSummaryDto(project);
    }

    // ---------------------
    // ItemType
    // ---------------------
    public ItemTypeViewDto toItemTypeDTO(ItemType itemType) {
        return itemTypeMapper.toDto(itemType);
    }

    public ItemType toItemType(ItemTypeCreateDto dto) {
        return itemTypeMapper.toEntity(dto);
    }

    public void updateItemTypeFromDto(ItemTypeCreateDto dto, @MappingTarget ItemType entity) {
        itemTypeMapper.updateItemTypeFromDto(dto, entity);
    }


    // ---------------------
    // Status
    // ---------------------
    public StatusViewDto toStatusViewDto(Status status) {
        return statusMapper.toViewDto(status);
    }

    public List<StatusViewDto> toStatusViewDtos(List<Status> statuses) {
        return statusMapper.toViewDtos(statuses);
    }

    public WorkflowSimpleDto workflowToWorkflowSimpleDto(Workflow workflow) { return statusMapper.workflowToWorkflowSimpleDto(workflow); }

    public StatusDetailDto toStatusDetailDto(Status status, List<Workflow> workflows) { return statusMapper.toStatusDetailDto(status, workflows); }


    // ---------------------
    // Transition
    // ---------------------
    public TransitionViewDto toTransitionViewDto(Transition transition) {
        return transitionMapper.toViewDto(transition);
    }


    // ---------------------
    // ItemTypeSet
    // ---------------------
    public ItemTypeSetViewDto toItemTypeSetViewDto(ItemTypeSet set) {
        return itemTypeSetMapper.toViewDto(set);
    }


    // ---------------------
    // Field
    // ---------------------
    public FieldViewDto toFieldViewDto(Field field) {
        return fieldMapper.toDto(field);
    }

    public Field toField(FieldCreateDto dto) {
        return fieldMapper.toEntity(dto);
    }

    public FieldDetailDto toFieldDetailDto(Field field, List<FieldConfiguration> fieldConfigurations, List<FieldSet> fieldSets) {
        return fieldMapper.toDetailDto(field, fieldConfigurations, fieldSets);
    }

    // ---------------------
    // Field Set
    // ---------------------
    public FieldSetViewDto toFieldSetViewDto(FieldSet fieldSet) {
        return fieldSetMapper.toViewDto(fieldSet);
    }

    // ---------------------
    // Field Set Entry
    // ---------------------
    public FieldSetEntryViewDto toFieldSetEntryViewDto(FieldSetEntry entry) {
            return fieldSetEntryMapper.toViewDto(entry);
    }

    public List<FieldSetEntryViewDto> toFieldSetEntryViewDtoList(List<FieldSetEntry> entries) {
        return fieldSetEntryMapper.toViewDtoList(entries);
    }

    // ---------------------
    // Field Option
    // ---------------------
    public FieldOptionViewDto toFieldOptionViewDto(FieldOption option) {
        return fieldOptionMapper.toViewDto(option);
    }

    public FieldOption toFieldOptionEntity(FieldOptionCreateDto dto) {
        return fieldOptionMapper.toEntity(dto);
    }

    public Set<FieldOption> toFieldOptionEntitySetFromCreate(Set<FieldOptionCreateDto> dtos) {
        return fieldOptionMapper.toEntitySetFromCreate(dtos);
    }

    public FieldOption toFieldOptionEntity(FieldOptionUpdateDto dto) {
        return fieldOptionMapper.toEntity(dto);
    }

    public Set<FieldOption> toFieldOptionEntitySetFromUpdate(Set<FieldOptionUpdateDto> dtos) {
        return fieldOptionMapper.toEntitySetFromUpdate(dtos);
    }


    // ---------------------
    // Field Configuration
    // ---------------------
    public FieldConfigurationDto toFieldConfigurationDto(FieldConfiguration entity) {
        return fieldConfigurationMapper.toDto(entity);
    }

    public List<FieldConfigurationDto> toFieldConfigurationDtos(List<FieldConfiguration> entities) {
        return  fieldConfigurationMapper.toDtos(entities);
    }

    public FieldConfigurationViewDto toFieldConfigurationViewDto(FieldConfiguration entity) {
        return fieldConfigurationMapper.toViewDto(entity);
    }

    public List<FieldConfigurationViewDto> toFieldConfigurationViewDtos(List<FieldConfiguration> entities) {
        return fieldConfigurationMapper.toViewDtos(entities);
    }

    public FieldConfiguration toFieldConfigurationEntity(
            FieldConfigurationCreateDto dto,
            FieldSet fieldSet,
            FieldRepository fieldRepository,
            ScopeType scopeType
    ) {
        return fieldConfigurationMapper.toFieldConfigurationEntity(dto, fieldSet, fieldRepository, scopeType);
    }


    // ---------------------
    // Field Type Descriptor
    // ---------------------
    public FieldTypeDescriptorDto toFieldTypeDescriptorDto(FieldType fieldType) {
        return fieldTypeDescriptorMapper.toDto(fieldType);
    }

    // ---------------------
    // Workflow
    // ---------------------

    public WorkflowViewDto toWorkflowViewDto (Workflow workflow) {
        return workflowMapper.toViewDto(workflow);
    }
    public List<WorkflowViewDto> toWorkflowViewDtos (List<Workflow> workflows) {
        return workflowMapper.toViewDtos(workflows);
    }

    // ---------------------
    // Workflowstatus
    // ---------------------
    public WorkflowStatusViewDto toWorkflowStatusViewDto (WorkflowStatus workflowStatus) {
        return workflowStatusMapper.toViewDto(workflowStatus);
    }

    public List<WorkflowStatusViewDto> toWorkflowStatusViewDtos (List<WorkflowStatus> workflowStatus) {
        return workflowStatusMapper.toViewDtos(workflowStatus);
    }

}
