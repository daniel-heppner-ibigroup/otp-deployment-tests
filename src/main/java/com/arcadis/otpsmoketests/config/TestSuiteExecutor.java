package com.arcadis.otpsmoketests.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes test suites with deployment-specific context.
 * This class handles the execution of JUnit test suites and provides
 * detailed execution results including metrics and error information.
 */
public class TestSuiteExecutor {

  private static final Logger logger = LoggerFactory.getLogger(
    TestSuiteExecutor.class
  );

  private final String deploymentName;
  private final Class<?> testSuiteClass;
  private final DeploymentContext deploymentContext;
  private final DeploymentConfiguration.TestSuiteConfig testSuiteConfig;
  private final DeploymentConfiguration.GeocodingConfig geocodingConfig;
  private final TestExecutorFactory factory;

  /**
   * Creates a new TestSuiteExecutor.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteClass The test suite class to execute
   * @param deploymentContext The deployment context
   * @param testSuiteConfig The test suite configuration
   * @param geocodingConfig The geocoding configuration
   */
  public TestSuiteExecutor(
    String deploymentName,
    Class<?> testSuiteClass,
    DeploymentContext deploymentContext,
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig,
    DeploymentConfiguration.GeocodingConfig geocodingConfig
  ) {
    this.deploymentName = deploymentName;
    this.testSuiteClass = testSuiteClass;
    this.deploymentContext = deploymentContext;
    this.testSuiteConfig = testSuiteConfig;
    this.geocodingConfig = geocodingConfig;
    this.factory = new TestExecutorFactory();
  }

  /**
   * Executes the test suite and returns execution results.
   *
   * @return TestExecutionResult containing execution details
   */
  public TestExecutionResult executeTests() {
    logger.info(
      "Executing test suite '{}' for deployment '{}'",
      testSuiteClass.getSimpleName(),
      deploymentName
    );

    long startTime = System.currentTimeMillis();

    try {
      // Validate that the test suite can be instantiated before execution
      factory.createTestSuiteInstance(testSuiteClass, deploymentContext, geocodingConfig);

      // Execute tests using JUnit Platform
      TestExecutionResult result = executeWithJUnitPlatform();

      long executionTime = System.currentTimeMillis() - startTime;
      result.setExecutionTimeMs(executionTime);

      logger.info(
        "Test suite '{}' for deployment '{}' completed in {}ms - {} tests run, {} failures",
        testSuiteClass.getSimpleName(),
        deploymentName,
        executionTime,
        result.getTestsRun(),
        result.getFailureCount()
      );

      return result;
    } catch (Exception e) {
      long executionTime = System.currentTimeMillis() - startTime;
      logger.error(
        "Failed to execute test suite '{}' for deployment '{}' after {}ms",
        testSuiteClass.getSimpleName(),
        deploymentName,
        executionTime,
        e
      );

      return TestExecutionResult.failure(
        deploymentName,
        testSuiteClass.getSimpleName(),
        e,
        executionTime
      );
    }
  }

  /**
   * Executes the test suite using JUnit Platform Launcher.
   *
   * @return TestExecutionResult with execution details
   */
  private TestExecutionResult executeWithJUnitPlatform() {
    Launcher launcher = LauncherFactory.create();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();

    LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
      .request()
      .selectors(DiscoverySelectors.selectClass(testSuiteClass))
      .build();

    launcher.registerTestExecutionListeners(listener);
    launcher.execute(request);

    var summary = listener.getSummary();

    TestExecutionResult result = new TestExecutionResult();
    result.setDeploymentName(deploymentName);
    result.setTestSuiteName(testSuiteClass.getSimpleName());

    // Use the actual test counts from the summary
    result.setTestsRun((int) summary.getTestsStartedCount());
    result.setSuccessCount((int) summary.getTestsSucceededCount());
    result.setFailureCount((int) summary.getTestsFailedCount());
    result.setSkippedCount((int) summary.getTestsSkippedCount());
    result.setSuccess(
      summary.getTestsFailedCount() == 0 && summary.getTestsAbortedCount() == 0
    );

    // Collect failure details with more comprehensive error information
    if (
      summary.getTestsFailedCount() > 0 || summary.getTestsAbortedCount() > 0
    ) {
      List<String> failures = new ArrayList<>();

      // Add failed test details
      summary
        .getFailures()
        .forEach(failure -> {
          String testName = failure.getTestIdentifier().getDisplayName();
          Throwable exception = failure.getException();
          String failureMessage = String.format(
            "FAILED: %s - %s: %s",
            testName,
            exception.getClass().getSimpleName(),
            exception.getMessage()
          );
          failures.add(failureMessage);

          logger.debug(
            "Test failure in '{}' for deployment '{}': {}",
            testName,
            deploymentName,
            exception.getMessage(),
            exception
          );
        });

      result.setFailureMessages(failures);
    }

    logger.debug(
      "Test execution summary for '{}' on '{}': {} started, {} succeeded, {} failed, {} skipped, {} aborted",
      testSuiteClass.getSimpleName(),
      deploymentName,
      summary.getTestsStartedCount(),
      summary.getTestsSucceededCount(),
      summary.getTestsFailedCount(),
      summary.getTestsSkippedCount(),
      summary.getTestsAbortedCount()
    );

    return result;
  }

  /**
   * Gets the deployment name for this executor.
   *
   * @return The deployment name
   */
  public String getDeploymentName() {
    return deploymentName;
  }

  /**
   * Gets the test suite class for this executor.
   *
   * @return The test suite class
   */
  public Class<?> getTestSuiteClass() {
    return testSuiteClass;
  }

  /**
   * Gets the deployment context for this executor.
   *
   * @return The deployment context
   */
  public DeploymentContext getDeploymentContext() {
    return deploymentContext;
  }

  /**
   * Gets the test suite configuration for this executor.
   *
   * @return The test suite configuration
   */
  public DeploymentConfiguration.TestSuiteConfig getTestSuiteConfig() {
    return testSuiteConfig;
  }

  /**
   * Checks if the test suite is enabled.
   *
   * @return true if the test suite is enabled
   */
  public boolean isEnabled() {
    return testSuiteConfig.isEnabled();
  }

  /**
   * Gets the schedule expression for this test suite.
   *
   * @return The schedule expression
   */
  public String getSchedule() {
    return testSuiteConfig.getSchedule();
  }

  /**
   * Counts the number of test methods in the test suite class.
   *
   * @return The number of test methods
   */
  public int getTestMethodCount() {
    int count = 0;
    for (Method method : testSuiteClass.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Test.class)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets a display name for this executor.
   *
   * @return A formatted display name
   */
  public String getDisplayName() {
    return String.format(
      "%s on %s",
      testSuiteClass.getSimpleName(),
      deploymentContext.getDisplayName()
    );
  }

  /**
   * Validates that this test suite executor is properly configured and can execute tests.
   *
   * @return true if the executor is valid and ready to run tests
   */
  public boolean isValid() {
    try {
      // Check if the test suite class can be instantiated
      factory.createTestSuiteInstance(testSuiteClass, deploymentContext, geocodingConfig);

      // Check if the test suite has any test methods
      int testMethodCount = getTestMethodCount();
      if (testMethodCount == 0) {
        logger.warn(
          "Test suite '{}' has no test methods",
          testSuiteClass.getSimpleName()
        );
        return false;
      }

      logger.debug(
        "Test suite executor validation passed for '{}' on '{}' - {} test methods found",
        testSuiteClass.getSimpleName(),
        deploymentName,
        testMethodCount
      );

      return true;
    } catch (Exception e) {
      logger.error(
        "Test suite executor validation failed for '{}' on '{}': {}",
        testSuiteClass.getSimpleName(),
        deploymentName,
        e.getMessage(),
        e
      );
      return false;
    }
  }

  /**
   * Executes a dry run to validate the test suite without actually running the tests.
   * This can be useful for configuration validation.
   *
   * @return TestExecutionResult indicating whether the dry run was successful
   */
  public TestExecutionResult dryRun() {
    logger.debug(
      "Performing dry run for test suite '{}' on deployment '{}'",
      testSuiteClass.getSimpleName(),
      deploymentName
    );

    long startTime = System.currentTimeMillis();

    try {
      // Validate that the test suite can be instantiated
      factory.createTestSuiteInstance(testSuiteClass, deploymentContext, geocodingConfig);

      // Count test methods
      int testMethodCount = getTestMethodCount();

      long executionTime = System.currentTimeMillis() - startTime;

      logger.debug(
        "Dry run successful for '{}' on '{}' - {} test methods found in {}ms",
        testSuiteClass.getSimpleName(),
        deploymentName,
        testMethodCount,
        executionTime
      );

      return TestExecutionResult.success(
        deploymentName,
        testSuiteClass.getSimpleName(),
        testMethodCount,
        executionTime
      );
    } catch (Exception e) {
      long executionTime = System.currentTimeMillis() - startTime;

      logger.error(
        "Dry run failed for '{}' on '{}' after {}ms",
        testSuiteClass.getSimpleName(),
        deploymentName,
        executionTime,
        e
      );

      return TestExecutionResult.failure(
        deploymentName,
        testSuiteClass.getSimpleName(),
        e,
        executionTime
      );
    }
  }

  @Override
  public String toString() {
    return (
      "TestSuiteExecutor{" +
      "deploymentName='" +
      deploymentName +
      '\'' +
      ", testSuiteClass=" +
      testSuiteClass.getSimpleName() +
      ", enabled=" +
      testSuiteConfig.isEnabled() +
      ", schedule='" +
      testSuiteConfig.getSchedule() +
      '\'' +
      '}'
    );
  }
}
