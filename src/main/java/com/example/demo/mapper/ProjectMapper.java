package com.example.demo.mapper;

import com.example.demo.dto.ProjectCreateDto;
import com.example.demo.dto.ProjectUpdateDto;
import com.example.demo.dto.ProjectViewDto;
import com.example.demo.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ItemTypeSetMapper.class})
public interface ProjectMapper {

    @Mapping(source = "projectKey", target = "key")
    @Mapping(source = "itemTypeSet", target = "itemTypeSet")
    ProjectViewDto toViewDto(Project project);

    List<ProjectViewDto> toViewDtos(List<Project> projects);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectKey", source = "key") // se nel DTO si chiama 'key' e in entity 'projectKey'
    @Mapping(target = "tenant", ignore = true) // tenant lo imposterai manualmente nel service
    @Mapping(target = "createdAt", ignore = true) // oppure gestito da DB
    @Mapping(target = "itemTypeSet", ignore = true) // da associare a parte
    Project toProject(ProjectCreateDto dto);

    @Mapping(target = "id", ignore = true) // id non si aggiorna
    @Mapping(target = "tenant", ignore = true) // tenant non viene aggiornato qui
    @Mapping(source = "key", target = "projectKey") // mappa key a projectKey
    @Mapping(target = "itemTypeSet", ignore = true) // se vuoi aggiornare anche l'itemTypeSet
    @Mapping(target = "createdAt", ignore = true)
    void updateProjectFromDto(ProjectUpdateDto dto, @MappingTarget Project project);

}
