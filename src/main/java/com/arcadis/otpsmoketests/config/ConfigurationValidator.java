package com.arcadis.otpsmoketests.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Validates deployment configuration for correctness and completeness.
 */
@Component
public class ConfigurationValidator {

  private static final Logger logger = LoggerFactory.getLogger(
    ConfigurationValidator.class
  );

  // Pattern for simple interval expressions like "10m", "30s", "2h"
  private static final Pattern SIMPLE_INTERVAL_PATTERN = Pattern.compile(
    "^\\d+[smh]$"
  );

  /**
   * Validates the entire deployment configuration.
   *
   * @param config the deployment configuration to validate
   * @return list of validation errors, empty if valid
   */
  public List<String> validateConfiguration(DeploymentConfiguration config) {
    List<String> errors = new ArrayList<>();

    if (config.getDeployments() == null || config.getDeployments().isEmpty()) {
      errors.add("No deployments configured");
      return errors;
    }

    for (var entry : config.getDeployments().entrySet()) {
      String deploymentKey = entry.getKey();
      DeploymentConfiguration.DeploymentConfig deployment = entry.getValue();

      errors.addAll(validateDeployment(deploymentKey, deployment));
    }

    return errors;
  }

  /**
   * Validates a single deployment configuration.
   */
  private List<String> validateDeployment(
    String deploymentKey,
    DeploymentConfiguration.DeploymentConfig deployment
  ) {
    List<String> errors = new ArrayList<>();
    String prefix = "Deployment '" + deploymentKey + "': ";

    // Validate deployment name
    if (deployment.getName() == null || deployment.getName().trim().isEmpty()) {
      errors.add(prefix + "name cannot be blank");
    }

    // Validate OTP URL
    if (!isValidUrl(deployment.getOtpUrl())) {
      errors.add(prefix + "invalid OTP URL format: " + deployment.getOtpUrl());
    }

    // Validate test suites
    if (
      deployment.getTestSuites() == null || deployment.getTestSuites().isEmpty()
    ) {
      logger.warn(prefix + "no test suites configured");
    } else {
      for (int i = 0; i < deployment.getTestSuites().size(); i++) {
        DeploymentConfiguration.TestSuiteConfig testSuite = deployment
          .getTestSuites()
          .get(i);
        errors.addAll(validateTestSuite(prefix + "test suite " + i, testSuite));
      }
    }

    return errors;
  }

  /**
   * Validates a test suite configuration.
   */
  private List<String> validateTestSuite(
    String prefix,
    DeploymentConfiguration.TestSuiteConfig testSuite
  ) {
    List<String> errors = new ArrayList<>();

    // Validate class name
    if (!isValidClassName(testSuite.getClassName())) {
      errors.add(
        prefix +
        ": invalid or non-existent class name: " +
        testSuite.getClassName()
      );
    }

    // Validate schedule expression
    if (!isValidScheduleExpression(testSuite.getSchedule())) {
      errors.add(
        prefix + ": invalid schedule expression: " + testSuite.getSchedule()
      );
    }

    return errors;
  }

  /**
   * Validates URL format.
   */
  public boolean isValidUrl(String url) {
    if (url == null || url.trim().isEmpty()) {
      return false;
    }

    try {
      new URL(url);
      return url.startsWith("http://") || url.startsWith("https://");
    } catch (MalformedURLException e) {
      return false;
    }
  }

  /**
   * Validates that a class name exists and can be loaded.
   */
  public boolean isValidClassName(String className) {
    if (className == null || className.trim().isEmpty()) {
      return false;
    }

    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      logger.warn("Test suite class not found: {}", className);
      return false;
    }
  }

  /**
   * Validates schedule expression (supports both cron expressions and simple intervals).
   */
  public boolean isValidScheduleExpression(String schedule) {
    if (schedule == null || schedule.trim().isEmpty()) {
      return false;
    }

    String trimmedSchedule = schedule.trim();

    // Try to parse as cron expression first
    if (isValidCronExpression(trimmedSchedule)) {
      return true;
    }

    // Try to parse as simple interval (e.g., "10m", "30s", "2h")
    if (SIMPLE_INTERVAL_PATTERN.matcher(trimmedSchedule).matches()) {
      return true;
    }

    return false;
  }

  /**
   * Validates cron expression format.
   */
  private boolean isValidCronExpression(String cronExpression) {
    try {
      CronExpression.parse(cronExpression);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
