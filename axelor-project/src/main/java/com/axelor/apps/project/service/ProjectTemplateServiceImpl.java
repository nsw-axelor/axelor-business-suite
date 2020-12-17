package com.axelor.apps.project.service;

import com.axelor.apps.project.db.ProjectTemplate;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.repo.ProjectTemplateRepository;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.util.Set;

public class ProjectTemplateServiceImpl implements ProjectTemplateService {

  protected ProjectTemplateRepository projectTemplateRepo;
  protected TaskTemplateService taskTemplateService;

  @Inject
  public ProjectTemplateServiceImpl(
      ProjectTemplateRepository projectTemplateRepo, TaskTemplateService taskTemplateService) {
    this.projectTemplateRepo = projectTemplateRepo;
    this.taskTemplateService = taskTemplateService;
  }

  @Override
  public ProjectTemplate addParentTaskTemplate(ProjectTemplate projectTemplate) {
    Set<TaskTemplate> taskTemplateSet = projectTemplate.getTaskTemplateSet();
    if (ObjectUtils.isEmpty(taskTemplateSet)) {
      return projectTemplate;
    }
    Set<TaskTemplate> newTaskTemplateSet =
        projectTemplate.getId() == null
            ? taskTemplateSet
            : taskTemplateService.getNewAddedTaskTemplate(
                projectTemplateRepo.find(projectTemplate.getId()).getTaskTemplateSet(),
                taskTemplateSet);
    if (ObjectUtils.isEmpty(newTaskTemplateSet)) {
      return projectTemplate;
    }
    projectTemplate.setTaskTemplateSet(
        taskTemplateService.getParentTaskTemplateFromTaskTemplates(
            newTaskTemplateSet, taskTemplateSet));
    return projectTemplate;
  }
}
