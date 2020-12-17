package com.axelor.apps.project.service;

import java.util.Set;
import com.axelor.apps.project.db.TaskTemplate;

public interface TaskTemplateService {

  Set<TaskTemplate> getNewAddedTaskTemplate(
      Set<TaskTemplate> oldTaskTemplateSet, Set<TaskTemplate> taskTemplateSet);

  Set<TaskTemplate> getParentTaskTemplateFromTaskTemplates(
      Set<TaskTemplate> newTaskTemplateSet, Set<TaskTemplate> taskTemplateSet);
}
