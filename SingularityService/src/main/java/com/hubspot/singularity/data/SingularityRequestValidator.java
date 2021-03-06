package com.hubspot.singularity.data;

import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.quartz.CronExpression;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.hubspot.singularity.SingularityRequest;

public class SingularityRequestValidator {

  private static final Joiner JOINER = Joiner.on(" ");
  
  private final SingularityRequest request;
  
  public SingularityRequestValidator(SingularityRequest request) {
    this.request = request;
  }
  
  @SuppressWarnings("serial")
  private static class InvalidRequestException extends WebApplicationException {

    public InvalidRequestException(String message) {
      super(Response.status(Status.BAD_REQUEST).entity(message).type("text/plain").build());
    }
   
  }
  
  private void checkRequestState(boolean expression, String message) {
    if (!expression) {
      throw new InvalidRequestException(message);
    }
  }
  
  public SingularityRequest buildValidRequest() throws InvalidRequestException {
    checkRequestState(request.getId() != null, "Id must not be null");
    checkRequestState(request.getInstances() == null || request.getInstances() > 0, "Instances must be greater than 0");
    checkRequestState(request.getSchedule() == null || ((request.getInstances() == null || request.getInstances() == 1) && (request.getDaemon() == null || !request.getDaemon())), "Scheduled requests can not be ran on more than one instance, and must not be daemons");
    checkRequestState((request.getDaemon() == null || request.getDaemon()) || (request.getInstances() == null || request.getInstances() == 1), "Non-daemons can not be ran on more than one instance");
    
    String schedule = adjustSchedule(request.getSchedule());
    
    checkRequestState(schedule == null || CronExpression.isValidExpression(schedule), "Cron schedule was not parseable");
    checkRequestState((request.getCommand() != null && request.getExecutorData() == null) || (request.getExecutorData() != null && request.getExecutor() != null && request.getCommand() == null), 
    "If not using custom executor, specify a command. If using custom executor, specify executorData OR command.");
        
    checkRequestState(request.getResources() == null || request.getResources().getNumPorts() == 0 || (request.getExecutor() == null || (request.getExecutorData() != null && request.getExecutorData() instanceof Map)), 
        "Requiring ports requires a custom executor with a json executor data payload OR not using a custom executor");
        
    return new SingularityRequest(request.getCommand(), request.getName(), request.getExecutor(), request.getResources(), schedule, Objects.firstNonNull(request.getInstances(), 1), request.getDaemon(), request.getEnv(), 
        request.getUris(), request.getMetadata(),  request.getExecutorData(), request.getRackSensitive(), request.getId(), request.getVersion(), request.getTimestamp());
  }
  
  /**
   * 
   * Transforms unix cron into fucking quartz cron; adding seconds if not passed
   * in and switching either day of month or day of week to ?
   * 
   * Field Name Allowed Values Allowed Special Characters Seconds 0-59 , - * /
   * Minutes 0-59 , - * / Hours 0-23 , - * / Day-of-month 1-31 , - * ? / L W
   * Month 1-12 or JAN-DEC , - * / Day-of-Week 1-7 or SUN-SAT , - * ? / L # Year
   * (Optional) empty, 1970-2199 , - * /
   */
  private String adjustSchedule(String schedule) throws InvalidRequestException {
    if (schedule == null) {
      return null;
    }

    String[] split = schedule.split(" ");

    if (split.length < 4) {
      throw new InvalidRequestException(String.format("Schedule %s is invalid", schedule));
    }

    List<String> newSchedule = Lists.newArrayListWithCapacity(6);

    boolean hasSeconds = split.length > 5;

    if (!hasSeconds) {
      newSchedule.add("0");
    } else {
      newSchedule.add(split[0]);
    }

    int indexMod = hasSeconds ? 1 : 0;

    newSchedule.add(split[indexMod + 0]);
    newSchedule.add(split[indexMod + 1]);

    String dayOfMonth = split[indexMod + 2];
    String dayOfWeek = split[indexMod + 4];

    if (dayOfWeek.equals("*")) {
      dayOfWeek = "?";
    } else if (!dayOfWeek.equals("?")) {
      dayOfMonth = "?";
    }

    newSchedule.add(dayOfMonth);
    newSchedule.add(split[indexMod + 3]);
    newSchedule.add(dayOfWeek);

    return JOINER.join(newSchedule);
  }
  
}
