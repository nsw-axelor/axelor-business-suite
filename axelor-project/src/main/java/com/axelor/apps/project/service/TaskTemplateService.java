package com.axelor.apps.project.service;

import com.axelor.apps.project.db.TaskTemplate;
import java.util.Set;

public interface TaskTemplateService {

  Set<TaskTemplate> getNewAddedTaskTemplate(
      Set<TaskTemplate> oldTaskTemplateSet, Set<TaskTemplate> taskTemplateSet);

  Set<TaskTemplate> getParentTaskTemplateFromTaskTemplates(
      Set<TaskTemplate> newTaskTemplateSet, Set<TaskTemplate> taskTemplateSet);
}
