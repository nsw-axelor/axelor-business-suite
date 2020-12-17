package com.axelor.apps.project.service;

import java.util.Set;
import java.util.stream.Collectors;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.common.ObjectUtils;

public class TaskTemplateServiceImpl implements TaskTemplateService {

  @Override
  public Set<TaskTemplate> getNewAddedTaskTemplate(
      Set<TaskTemplate> oldTaskTemplateSet, Set<TaskTemplate> taskTemplateSet) {
    return ObjectUtils.isEmpty(oldTaskTemplateSet)
        ? taskTemplateSet
        : taskTemplateSet
            .stream()
            .filter(it -> !oldTaskTemplateSet.contains(it))
            .collect(Collectors.toSet());
  }

  @Override
  public Set<TaskTemplate> getParentTaskTemplateFromTaskTemplates(
      Set<TaskTemplate> newTaskTemplateSet, Set<TaskTemplate> taskTemplateSet) {
    for (TaskTemplate taskTemplate : newTaskTemplateSet) {
      taskTemplateSet.addAll(
          this.getParentTaskTemplateFromTaskTemplate(
              taskTemplate.getParentTaskTemplate(), taskTemplateSet));
    }
//    newTaskTemplateSet.stream().forEach( it -> {
//      taskTemplateSet.addAll(
//        this.getParentTaskTemplateFromTaskTemplate(
//            it.getParentTaskTemplate(), taskTemplateSet));
//    });
    return taskTemplateSet;
  }

  private Set<TaskTemplate> getParentTaskTemplateFromTaskTemplate(
      TaskTemplate taskTemplate, Set<TaskTemplate> taskTemplateSet) {
    if (taskTemplate == null || taskTemplateSet.contains(taskTemplate)) {
      return taskTemplateSet;
    }
    taskTemplateSet.add(taskTemplate);
    taskTemplateSet.addAll(
        this.getParentTaskTemplateFromTaskTemplate(
            taskTemplate.getParentTaskTemplate(), taskTemplateSet));
    return taskTemplateSet;
  }
}
