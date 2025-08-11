package com.arcadis.otpsmoketests.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads and validates deployment configuration on application startup.
 */
@Component
public class ConfigurationLoader {

  private static final Logger logger = LoggerFactory.getLogger(
    ConfigurationLoader.class
  );

  private final DeploymentConfiguration deploymentConfiguration;
  private final ConfigurationValidator configurationValidator;

  @Autowired
  public ConfigurationLoader(
    DeploymentConfiguration deploymentConfiguration,
    ConfigurationValidator configurationValidator
  ) {
    this.deploymentConfiguration = deploymentConfiguration;
    this.configurationValidator = configurationValidator;
  }

  /**
   * Validates configuration after application startup.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void validateConfigurationOnStartup() {
    logger.info("Loading and validating deployment configuration...");

    List<String> validationErrors = configurationValidator.validateConfiguration(
      deploymentConfiguration
    );

    if (validationErrors.isEmpty()) {
      logger.info(
        "Configuration validation successful. Found {} deployments.",
        deploymentConfiguration.getDeployments().size()
      );

      // Log deployment summary
      deploymentConfiguration
        .getDeployments()
        .forEach((key, deployment) -> {
          int enabledTestSuites = (int) deployment
            .getTestSuites()
            .stream()
            .filter(DeploymentConfiguration.TestSuiteConfig::isEnabled)
            .count();
          logger.info(
            "Deployment '{}': {} enabled test suites",
            deployment.getName(),
            enabledTestSuites
          );
        });
    } else {
      logger.error(
        "Configuration validation failed with {} errors:",
        validationErrors.size()
      );
      validationErrors.forEach(error -> logger.error("  - {}", error));

      // Continue startup but log warning
      logger.warn(
        "Application will start with default configuration due to validation errors"
      );
    }
  }

  /**
   * Gets the validated deployment configuration.
   */
  public DeploymentConfiguration getDeploymentConfiguration() {
    return deploymentConfiguration;
  }

  /**
   * Checks if the configuration is valid.
   */
  public boolean isConfigurationValid() {
    return configurationValidator
      .validateConfiguration(deploymentConfiguration)
      .isEmpty();
  }
}
