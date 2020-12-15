package com.axelor.apps.project.service;

import com.axelor.apps.project.db.ProjectTemplate;
import com.axelor.apps.project.db.repo.ProjectTemplateRepository;
import com.google.inject.Inject;

public class ProjectTemplateServiceImpl implements ProjectTemplateService {
  
  protected ProjectTemplateRepository projectTemplateRepo;

  @Inject
  public ProjectTemplateServiceImpl(ProjectTemplateRepository projectTemplateRepo) {
    this.projectTemplateRepo = projectTemplateRepo;
  }

  @Override
  public ProjectTemplate addParentTaskTemplate(ProjectTemplate projectTemplate) {
    ProjectTemplate oldProjectTemplate = projectTemplateRepo.find(projectTemplate.getId());
    
    return projectTemplate;
  }
}
