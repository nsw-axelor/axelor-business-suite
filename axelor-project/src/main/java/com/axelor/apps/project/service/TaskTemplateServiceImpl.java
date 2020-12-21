/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.project.service;

import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.common.ObjectUtils;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TaskTemplateServiceImpl implements TaskTemplateService {

  @Override
  public Set<TaskTemplate> getNewAddedTaskTemplate(
      Set<TaskTemplate> oldTaskTemplateSet, Set<TaskTemplate> taskTemplateSet) {
    return ObjectUtils.isEmpty(oldTaskTemplateSet)
        ? new HashSet<>(taskTemplateSet)
        : taskTemplateSet.stream()
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
