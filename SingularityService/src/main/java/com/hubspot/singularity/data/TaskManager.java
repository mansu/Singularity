package com.hubspot.singularity.data;

import java.util.Collections;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.hubspot.singularity.SingularityTask;
import com.hubspot.singularity.SingularityTaskId;

public class TaskManager {

  private final static Logger LOG = LoggerFactory.getLogger(TaskManager.class);
  
  private final CuratorFramework curator;
  private final ObjectMapper objectMapper;
  
  private final static String ACTIVE_PATH_ROOT = "/tasks";
  private final static String ACTIVE_PATH_FORMAT = ACTIVE_PATH_ROOT + "/%s";

  private final static String PENDING_PATH_ROOT = "/pending";
  private final static String PENDING_PATH_FORMAT = PENDING_PATH_ROOT + "/%s";
    
  @Inject
  public TaskManager(CuratorFramework curator, ObjectMapper objectMapper) {
    this.curator = curator;
    this.objectMapper = objectMapper;
  }
  
  private String getActivePath(String taskId) {
    return String.format(ACTIVE_PATH_FORMAT, taskId);
  }

  private String getPendingPath(String taskId) {
    return String.format(PENDING_PATH_FORMAT, taskId);
  }

  public void persistScheduleTasks(List<SingularityTaskId> taskIds) {
    try {
      for (SingularityTaskId taskId : taskIds) {
        persistTaskId(taskId);
      }
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }

  private void persistTaskId(SingularityTaskId taskId) throws Exception {
    final String pendingPath = getPendingPath(taskId.toString());

    curator.create().creatingParentsIfNeeded().forPath(pendingPath);
  }
  
  private List<SingularityTaskId> getTasksForRoot(String root) {
    try {
      List<String> taskIds = curator.getChildren().forPath(root);
      List<SingularityTaskId> tasksIdsObjs = Lists.newArrayListWithCapacity(taskIds.size());
      
      for (String taskId : taskIds) {
        SingularityTaskId taskIdObj = SingularityTaskId.fromString(taskId);
        tasksIdsObjs.add(taskIdObj);
      }

      return tasksIdsObjs;
    } catch (NoNodeException nne) {
      return Collections.emptyList();
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
  
  public List<SingularityTaskId> getActiveTaskIds() {
    return getTasksForRoot(ACTIVE_PATH_ROOT);
  }
  
  public Optional<SingularityTask> getActiveTask(String taskId) {
    final String path = getActivePath(taskId);
    
    try {
      byte[] data = curator.getData().forPath(path);
      
      SingularityTask task = SingularityTask.getTaskFromData(data, objectMapper);
      
      return Optional.of(task);
    } catch (NoNodeException nne) {
      return Optional.absent();
    } catch (Exception e) {
      throw Throwables.propagate(e); 
    }
  }
  
  public List<SingularityTask> getActiveTasks() {
    List<SingularityTaskId> taskIds = getActiveTaskIds();
    
    try {
      List<SingularityTask> tasks = Lists.newArrayListWithCapacity(taskIds.size());
      
      for (SingularityTaskId taskId : taskIds) {
        Optional<SingularityTask> maybeTask = getActiveTask(taskId.toString());
        
        if (maybeTask.isPresent()) {
          tasks.add(maybeTask.get());
        } else {
          LOG.info(String.format("Expected active node %s but it wasn't there", taskId));
        }
      }

      return tasks;
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }

  public List<SingularityTaskId> getPendingTasks() {
    return getTasksForRoot(PENDING_PATH_ROOT);
  }

  public void launchTask(SingularityTask task) {
    try {
      launchTaskPrivate(task);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }

  private void launchTaskPrivate(SingularityTask task) throws Exception {
    final String pendingPath = getPendingPath(task.getTaskRequest().getTaskId().toString());
    final String activePath = getActivePath(task.getTaskRequest().getTaskId().toString());
    
    curator.delete().forPath(pendingPath);
    
    curator.create().creatingParentsIfNeeded().forPath(activePath, task.getTaskData(objectMapper));
  }
    
  public void deleteActiveTask(String taskId) {
    final String activePath = getActivePath(taskId);
    
    try {
      curator.delete().forPath(activePath);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
  
}