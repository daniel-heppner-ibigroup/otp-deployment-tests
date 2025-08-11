package com.arcadis.otpsmoketests.config;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

/**
 * Manages scheduling of test suite executions across multiple deployments.
 * Provides functionality for scheduling, rescheduling, and canceling test suite tasks
 * with support for cron expressions and task tracking.
 */
@Component
public class ScheduleManager {

  private static final Logger logger = LoggerFactory.getLogger(
    ScheduleManager.class
  );

  private static final String DEFAULT_SCHEDULE = "0 */10 * * * *"; // Every 10 minutes

  // Pattern for simple interval expressions like "10m", "30s", "2h"
  private static final Pattern SIMPLE_INTERVAL_PATTERN = Pattern.compile(
    "^(\\d+)([smh])$"
  );

  private final TaskScheduler taskScheduler;
  private final TestExecutorFactory testExecutorFactory;
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  public ScheduleManager(
    TaskScheduler taskScheduler,
    TestExecutorFactory testExecutorFactory
  ) {
    this.taskScheduler = taskScheduler;
    this.testExecutorFactory = testExecutorFactory;
  }

  /**
   * Schedules a test suite for execution based on the provided configuration.
   *
   * @param deploymentName The name of the deployment
   * @param deployment The deployment configuration
   * @param testSuiteConfig The test suite configuration including schedule
   * @param geocodingConfig The geocoding configuration
   */
  public void scheduleTestSuite(
    String deploymentName,
    DeploymentConfiguration.DeploymentConfig deployment,
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig,
    DeploymentConfiguration.GeocodingConfig geocodingConfig
  ) {
    if (!testSuiteConfig.isEnabled()) {
      logger.debug(
        "Skipping disabled test suite '{}' for deployment '{}'",
        testSuiteConfig.getClassName(),
        deploymentName
      );
      return;
    }

    String taskId = generateTaskId(
      deploymentName,
      testSuiteConfig.getClassName()
    );

    logger.info(
      "Scheduling test suite '{}' for deployment '{}' with schedule '{}'",
      testSuiteConfig.getClassName(),
      deploymentName,
      testSuiteConfig.getSchedule()
    );

    try {
      // Create the test executor
      TestSuiteExecutor executor = testExecutorFactory.createExecutor(
        deploymentName,
        deployment,
        testSuiteConfig,
        geocodingConfig
      );

      // Parse and create the appropriate trigger
      Trigger trigger = createTrigger(testSuiteConfig.getSchedule());

      // Create the runnable task
      Runnable task = () -> {
        try {
          logger.debug(
            "Executing scheduled test suite '{}' for deployment '{}'",
            testSuiteConfig.getClassName(),
            deploymentName
          );
          executor.executeTests();
        } catch (Exception e) {
          logger.error(
            "Error executing scheduled test suite '{}' for deployment '{}': {}",
            testSuiteConfig.getClassName(),
            deploymentName,
            e.getMessage(),
            e
          );
        }
      };

      // Schedule the task
      ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(
        task,
        trigger
      );

      // Cancel any existing task with the same ID
      cancelScheduledTask(taskId);

      // Store the new scheduled task
      scheduledTasks.put(taskId, scheduledFuture);

      logger.info(
        "Successfully scheduled test suite '{}' for deployment '{}' with task ID '{}'",
        testSuiteConfig.getClassName(),
        deploymentName,
        taskId
      );
    } catch (Exception e) {
      logger.error(
        "Failed to schedule test suite '{}' for deployment '{}': {}",
        testSuiteConfig.getClassName(),
        deploymentName,
        e.getMessage(),
        e
      );
    }
  }

  /**
   * Reschedules all test suites based on the provided configuration.
   * This method cancels all existing schedules and creates new ones.
   *
   * @param config The complete deployment configuration
   */
  public void rescheduleAll(DeploymentConfiguration config) {
    logger.info("Rescheduling all test suites based on updated configuration");

    // Cancel all existing tasks
    cancelAllTasks();

    // Schedule all configured test suites
    config
      .getDeployments()
      .forEach((deploymentName, deployment) -> {
        deployment
          .getTestSuites()
          .forEach(testSuiteConfig -> {
            scheduleTestSuite(deploymentName, deployment, testSuiteConfig, config.getGeocoding());
          });
      });

    logger.info(
      "Completed rescheduling. Active schedules: {}",
      scheduledTasks.size()
    );
  }

  /**
   * Cancels a scheduled task by its task ID.
   *
   * @param taskId The unique task identifier
   * @return true if the task was found and canceled, false otherwise
   */
  public boolean cancelScheduledTask(String taskId) {
    ScheduledFuture<?> scheduledFuture = scheduledTasks.remove(taskId);
    if (scheduledFuture != null) {
      boolean canceled = scheduledFuture.cancel(false);
      logger.info("Canceled scheduled task '{}': {}", taskId, canceled);
      return canceled;
    }
    return false;
  }

  /**
   * Returns the set of active schedule task IDs.
   *
   * @return Set of active task IDs
   */
  public Set<String> getActiveSchedules() {
    // Remove completed tasks from the map
    scheduledTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
    return Set.copyOf(scheduledTasks.keySet());
  }

  /**
   * Cancels all scheduled tasks.
   */
  public void cancelAllTasks() {
    logger.info("Canceling all {} scheduled tasks", scheduledTasks.size());

    scheduledTasks
      .values()
      .forEach(future -> {
        try {
          future.cancel(false);
        } catch (Exception e) {
          logger.warn("Error canceling scheduled task: {}", e.getMessage());
        }
      });

    scheduledTasks.clear();
    logger.info("All scheduled tasks have been canceled");
  }

  /**
   * Generates a unique task ID for a deployment and test suite combination.
   *
   * @param deploymentName The deployment name
   * @param testSuiteClassName The test suite class name
   * @return A unique task identifier
   */
  private String generateTaskId(
    String deploymentName,
    String testSuiteClassName
  ) {
    // Extract simple class name for readability
    String simpleClassName = testSuiteClassName.substring(
      testSuiteClassName.lastIndexOf('.') + 1
    );
    return String.format("%s-%s", deploymentName, simpleClassName);
  }

  /**
   * Creates a Trigger based on the schedule expression.
   * Supports both cron expressions and simple interval notation.
   * Falls back to default schedule if the expression is invalid.
   *
   * @param scheduleExpression The schedule expression to parse
   * @return A Trigger instance
   */
  private Trigger createTrigger(String scheduleExpression) {
    if (scheduleExpression == null || scheduleExpression.trim().isEmpty()) {
      logger.warn(
        "Empty schedule expression, using default: {}",
        DEFAULT_SCHEDULE
      );
      return new CronTrigger(DEFAULT_SCHEDULE);
    }

    String trimmedSchedule = scheduleExpression.trim();

    // Try to parse as cron expression first
    try {
      CronExpression.parse(trimmedSchedule);
      logger.debug("Using cron trigger for schedule: {}", trimmedSchedule);
      return new CronTrigger(trimmedSchedule);
    } catch (IllegalArgumentException e) {
      logger.debug(
        "Not a valid cron expression, trying simple interval: {}",
        trimmedSchedule
      );
    }

    // Try to parse as simple interval (e.g., "10m", "30s", "2h")
    Matcher matcher = SIMPLE_INTERVAL_PATTERN.matcher(trimmedSchedule);
    if (matcher.matches()) {
      try {
        Duration interval = parseSimpleInterval(
          matcher.group(1),
          matcher.group(2)
        );
        logger.debug(
          "Using periodic trigger for schedule: {} ({})",
          trimmedSchedule,
          interval
        );
        return new PeriodicTrigger(interval);
      } catch (Exception e) {
        logger.warn(
          "Failed to parse simple interval '{}': {}",
          trimmedSchedule,
          e.getMessage()
        );
      }
    }

    // Fall back to default schedule
    logger.warn(
      "Invalid schedule expression '{}', using default '{}'",
      scheduleExpression,
      DEFAULT_SCHEDULE
    );
    return new CronTrigger(DEFAULT_SCHEDULE);
  }

  /**
   * Parses a simple interval expression into a Duration.
   *
   * @param value The numeric value
   * @param unit The time unit (s, m, h)
   * @return A Duration representing the interval
   */
  private Duration parseSimpleInterval(String value, String unit) {
    long numericValue = Long.parseLong(value);

    return switch (unit) {
      case "s" -> Duration.ofSeconds(numericValue);
      case "m" -> Duration.ofMinutes(numericValue);
      case "h" -> Duration.ofHours(numericValue);
      default -> throw new IllegalArgumentException(
        "Unsupported time unit: " + unit
      );
    };
  }

  /**
   * Validates a schedule expression without creating a trigger.
   * Supports both cron expressions and simple interval notation.
   *
   * @param scheduleExpression The schedule expression to validate
   * @return true if the expression is valid, false otherwise
   */
  public boolean isValidScheduleExpression(String scheduleExpression) {
    if (scheduleExpression == null || scheduleExpression.trim().isEmpty()) {
      return false;
    }

    String trimmedSchedule = scheduleExpression.trim();

    // Try to parse as cron expression
    try {
      CronExpression.parse(trimmedSchedule);
      return true;
    } catch (IllegalArgumentException e) {
      // Not a cron expression, try simple interval
    }

    // Try to parse as simple interval
    Matcher matcher = SIMPLE_INTERVAL_PATTERN.matcher(trimmedSchedule);
    if (matcher.matches()) {
      try {
        parseSimpleInterval(matcher.group(1), matcher.group(2));
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    return false;
  }

  /**
   * Returns the number of currently active scheduled tasks.
   *
   * @return The count of active tasks
   */
  public int getActiveTaskCount() {
    // Clean up completed tasks
    scheduledTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
    return scheduledTasks.size();
  }
}
