package com.arcadis.otpsmoketests.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Manages the lifecycle of test suite scheduling based on deployment configuration.
 * Handles initial scheduling on application startup and configuration updates.
 */
@Component
public class ScheduleConfigurationManager {

  private static final Logger logger = LoggerFactory.getLogger(
    ScheduleConfigurationManager.class
  );

  private final DeploymentConfiguration deploymentConfiguration;
  private final ScheduleManager scheduleManager;
  private final ConfigurationValidator configurationValidator;

  public ScheduleConfigurationManager(
    DeploymentConfiguration deploymentConfiguration,
    ScheduleManager scheduleManager,
    ConfigurationValidator configurationValidator
  ) {
    this.deploymentConfiguration = deploymentConfiguration;
    this.scheduleManager = scheduleManager;
    this.configurationValidator = configurationValidator;
  }

  /**
   * Initializes scheduling after the application context is fully loaded.
   * This ensures all beans are available before attempting to schedule tasks.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initializeScheduling() {
    logger.info("Initializing test suite scheduling from configuration");

    try {
      validateAndScheduleAll();
      logger.info(
        "Test suite scheduling initialization completed successfully"
      );
    } catch (Exception e) {
      logger.error(
        "Failed to initialize test suite scheduling: {}",
        e.getMessage(),
        e
      );
    }
  }

  /**
   * Validates the current configuration and schedules all enabled test suites.
   * This method can be called to refresh scheduling after configuration changes.
   */
  public void validateAndScheduleAll() {
    // Validate the configuration first
    List<String> validationErrors = configurationValidator.validateConfiguration(
      deploymentConfiguration
    );

    if (!validationErrors.isEmpty()) {
      logger.error(
        "Configuration validation failed with {} errors:",
        validationErrors.size()
      );
      validationErrors.forEach(error -> logger.error("  - {}", error));

      // Don't schedule anything if configuration is invalid
      return;
    }

    logger.info("Configuration validation passed, proceeding with scheduling");

    // Schedule all test suites
    scheduleAllTestSuites();
  }

  /**
   * Schedules all test suites from the current configuration.
   * Cancels existing schedules and creates new ones.
   */
  private void scheduleAllTestSuites() {
    int totalScheduled = 0;
    int totalSkipped = 0;

    for (var deploymentEntry : deploymentConfiguration
      .getDeployments()
      .entrySet()) {
      String deploymentName = deploymentEntry.getKey();
      DeploymentConfiguration.DeploymentConfig deployment = deploymentEntry.getValue();

      logger.debug("Processing deployment: {}", deploymentName);

      for (DeploymentConfiguration.TestSuiteConfig testSuiteConfig : deployment.getTestSuites()) {
        if (testSuiteConfig.isEnabled()) {
          try {
            scheduleManager.scheduleTestSuite(
              deploymentName,
              deployment,
              testSuiteConfig
            );
            totalScheduled++;
            logger.debug(
              "Scheduled test suite '{}' for deployment '{}'",
              testSuiteConfig.getClassName(),
              deploymentName
            );
          } catch (Exception e) {
            logger.error(
              "Failed to schedule test suite '{}' for deployment '{}': {}",
              testSuiteConfig.getClassName(),
              deploymentName,
              e.getMessage(),
              e
            );
            totalSkipped++;
          }
        } else {
          logger.debug(
            "Skipping disabled test suite '{}' for deployment '{}'",
            testSuiteConfig.getClassName(),
            deploymentName
          );
          totalSkipped++;
        }
      }
    }

    logger.info(
      "Scheduling completed: {} test suites scheduled, {} skipped",
      totalScheduled,
      totalSkipped
    );
  }

  /**
   * Refreshes all schedules based on the current configuration.
   * This method can be called when configuration changes are detected.
   */
  public void refreshSchedules() {
    logger.info("Refreshing all test suite schedules");

    try {
      // Use the ScheduleManager's rescheduleAll method which handles cleanup
      scheduleManager.rescheduleAll(deploymentConfiguration);
      logger.info("Schedule refresh completed successfully");
    } catch (Exception e) {
      logger.error("Failed to refresh schedules: {}", e.getMessage(), e);
    }
  }

  /**
   * Schedules a specific test suite for a deployment.
   * This method can be used to add individual schedules without affecting others.
   *
   * @param deploymentName The deployment name
   * @param testSuiteClassName The test suite class name
   * @return true if scheduling was successful, false otherwise
   */
  public boolean scheduleSpecificTestSuite(
    String deploymentName,
    String testSuiteClassName
  ) {
    DeploymentConfiguration.DeploymentConfig deployment = deploymentConfiguration
      .getDeployments()
      .get(deploymentName);

    if (deployment == null) {
      logger.warn("Deployment '{}' not found in configuration", deploymentName);
      return false;
    }

    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = deployment
      .getTestSuites()
      .stream()
      .filter(config -> config.getClassName().equals(testSuiteClassName))
      .findFirst()
      .orElse(null);

    if (testSuiteConfig == null) {
      logger.warn(
        "Test suite '{}' not found in deployment '{}' configuration",
        testSuiteClassName,
        deploymentName
      );
      return false;
    }

    if (!testSuiteConfig.isEnabled()) {
      logger.warn(
        "Test suite '{}' for deployment '{}' is disabled",
        testSuiteClassName,
        deploymentName
      );
      return false;
    }

    try {
      scheduleManager.scheduleTestSuite(
        deploymentName,
        deployment,
        testSuiteConfig
      );
      logger.info(
        "Successfully scheduled test suite '{}' for deployment '{}'",
        testSuiteClassName,
        deploymentName
      );
      return true;
    } catch (Exception e) {
      logger.error(
        "Failed to schedule test suite '{}' for deployment '{}': {}",
        testSuiteClassName,
        deploymentName,
        e.getMessage(),
        e
      );
      return false;
    }
  }

  /**
   * Cancels scheduling for a specific test suite.
   *
   * @param deploymentName The deployment name
   * @param testSuiteClassName The test suite class name
   * @return true if cancellation was successful, false otherwise
   */
  public boolean cancelSpecificTestSuite(
    String deploymentName,
    String testSuiteClassName
  ) {
    // Generate the same task ID that ScheduleManager would use
    String simpleClassName = testSuiteClassName.substring(
      testSuiteClassName.lastIndexOf('.') + 1
    );
    String taskId = String.format("%s-%s", deploymentName, simpleClassName);

    boolean canceled = scheduleManager.cancelScheduledTask(taskId);

    if (canceled) {
      logger.info(
        "Successfully canceled test suite '{}' for deployment '{}'",
        testSuiteClassName,
        deploymentName
      );
    } else {
      logger.warn(
        "Failed to cancel test suite '{}' for deployment '{}' - task not found",
        testSuiteClassName,
        deploymentName
      );
    }

    return canceled;
  }

  /**
   * Returns information about the current scheduling state.
   *
   * @return SchedulingStatus containing current state information
   */
  public SchedulingStatus getSchedulingStatus() {
    int configuredDeployments = deploymentConfiguration.getDeployments().size();

    int configuredTestSuites = deploymentConfiguration
      .getDeployments()
      .values()
      .stream()
      .mapToInt(deployment -> deployment.getTestSuites().size())
      .sum();

    int enabledTestSuites = deploymentConfiguration
      .getDeployments()
      .values()
      .stream()
      .mapToInt(deployment ->
        (int) deployment
          .getTestSuites()
          .stream()
          .filter(DeploymentConfiguration.TestSuiteConfig::isEnabled)
          .count()
      )
      .sum();

    int activeSchedules = scheduleManager.getActiveTaskCount();

    return new SchedulingStatus(
      configuredDeployments,
      configuredTestSuites,
      enabledTestSuites,
      activeSchedules,
      scheduleManager.getActiveSchedules()
    );
  }

  /**
   * Data class representing the current scheduling status.
   */
  public static class SchedulingStatus {

    private final int configuredDeployments;
    private final int configuredTestSuites;
    private final int enabledTestSuites;
    private final int activeSchedules;
    private final java.util.Set<String> activeTaskIds;

    public SchedulingStatus(
      int configuredDeployments,
      int configuredTestSuites,
      int enabledTestSuites,
      int activeSchedules,
      java.util.Set<String> activeTaskIds
    ) {
      this.configuredDeployments = configuredDeployments;
      this.configuredTestSuites = configuredTestSuites;
      this.enabledTestSuites = enabledTestSuites;
      this.activeSchedules = activeSchedules;
      this.activeTaskIds = activeTaskIds;
    }

    public int getConfiguredDeployments() {
      return configuredDeployments;
    }

    public int getConfiguredTestSuites() {
      return configuredTestSuites;
    }

    public int getEnabledTestSuites() {
      return enabledTestSuites;
    }

    public int getActiveSchedules() {
      return activeSchedules;
    }

    public java.util.Set<String> getActiveTaskIds() {
      return activeTaskIds;
    }

    @Override
    public String toString() {
      return String.format(
        "SchedulingStatus{deployments=%d, testSuites=%d, enabled=%d, active=%d, tasks=%s}",
        configuredDeployments,
        configuredTestSuites,
        enabledTestSuites,
        activeSchedules,
        activeTaskIds
      );
    }
  }
}
