package com.arcadis.otpsmoketests.config;

import com.arcadis.otpsmoketests.BaseTestSuite;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating test suite executors with deployment context.
 * This factory handles the instantiation of test suites with deployment-specific
 * configuration, including proper error handling for class loading and instantiation.
 */
@Component
public class TestExecutorFactory {

  private static final Logger logger = LoggerFactory.getLogger(
    TestExecutorFactory.class
  );

  /**
   * Creates a TestSuiteExecutor for the specified deployment and test suite configuration.
   *
   * @param deploymentName The name of the deployment
   * @param deployment The deployment configuration
   * @param testSuiteConfig The test suite configuration
   * @return A TestSuiteExecutor instance
   * @throws TestSuiteInstantiationException if the test suite cannot be created
   */
  public TestSuiteExecutor createExecutor(
    String deploymentName,
    DeploymentConfiguration.DeploymentConfig deployment,
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig
  ) {
    logger.debug(
      "Creating test executor for deployment '{}' with test suite '{}'",
      deploymentName,
      testSuiteConfig.getClassName()
    );

    DeploymentContext deploymentContext = new DeploymentContext(
      deployment.getName(),
      deployment.getOtpUrl()
    );

    Class<?> testSuiteClass = loadTestSuiteClass(testSuiteConfig.getClassName());

    return new TestSuiteExecutor(
      deploymentName,
      testSuiteClass,
      deploymentContext,
      testSuiteConfig
    );
  }

  /**
   * Loads the test suite class by name.
   *
   * @param className The fully qualified class name
   * @return The loaded Class object
   * @throws TestSuiteInstantiationException if the class cannot be loaded
   */
  private Class<?> loadTestSuiteClass(String className) {
    try {
      logger.debug("Loading test suite class: {}", className);
      Class<?> clazz = Class.forName(className);

      // Verify that the class extends BaseTestSuite
      if (!BaseTestSuite.class.isAssignableFrom(clazz)) {
        throw new TestSuiteInstantiationException(
          String.format(
            "Class '%s' does not extend BaseTestSuite",
            className
          )
        );
      }

      logger.debug("Successfully loaded test suite class: {}", className);
      return clazz;
    } catch (ClassNotFoundException e) {
      String message = String.format(
        "Test suite class not found: %s",
        className
      );
      logger.error(message, e);
      throw new TestSuiteInstantiationException(message, e);
    }
  }

  /**
   * Creates an instance of the test suite class with deployment context.
   * This method handles both legacy constructors (without deployment context)
   * and new constructors (with deployment context).
   *
   * @param testSuiteClass The test suite class to instantiate
   * @param deploymentContext The deployment context
   * @return An instance of the test suite
   * @throws TestSuiteInstantiationException if the test suite cannot be instantiated
   */
  public Object createTestSuiteInstance(
    Class<?> testSuiteClass,
    DeploymentContext deploymentContext
  ) {
    logger.debug(
      "Creating instance of test suite class: {}",
      testSuiteClass.getName()
    );

    try {
      // First, try to find a constructor that accepts DeploymentContext
      Constructor<?> contextConstructor = findDeploymentContextConstructor(
        testSuiteClass
      );
      if (contextConstructor != null) {
        logger.debug(
          "Using deployment context constructor for {}",
          testSuiteClass.getName()
        );
        return contextConstructor.newInstance(deploymentContext);
      }

      // Fall back to default constructor for legacy test suites
      logger.debug(
        "Using default constructor for legacy test suite: {}",
        testSuiteClass.getName()
      );
      Constructor<?> defaultConstructor = testSuiteClass.getDeclaredConstructor();
      return defaultConstructor.newInstance();
    } catch (
      NoSuchMethodException
      | InstantiationException
      | IllegalAccessException
      | InvocationTargetException e
    ) {
      String message = String.format(
        "Failed to instantiate test suite class: %s",
        testSuiteClass.getName()
      );
      logger.error(message, e);
      throw new TestSuiteInstantiationException(message, e);
    }
  }

  /**
   * Attempts to find a constructor that accepts DeploymentContext.
   * This supports future test suites that will be refactored to use deployment context.
   *
   * @param testSuiteClass The test suite class
   * @return The constructor if found, null otherwise
   */
  private Constructor<?> findDeploymentContextConstructor(
    Class<?> testSuiteClass
  ) {
    try {
      return testSuiteClass.getDeclaredConstructor(DeploymentContext.class);
    } catch (NoSuchMethodException e) {
      logger.debug(
        "No deployment context constructor found for {}",
        testSuiteClass.getName()
      );
      return null;
    }
  }

  /**
   * Validates that a test suite class can be instantiated.
   * This method performs validation without actually creating an instance.
   *
   * @param className The fully qualified class name
   * @return true if the class can be instantiated, false otherwise
   */
  public boolean validateTestSuiteClass(String className) {
    try {
      Class<?> clazz = loadTestSuiteClass(className);

      // Check if we can find a suitable constructor
      boolean hasDefaultConstructor = hasDefaultConstructor(clazz);
      boolean hasContextConstructor = findDeploymentContextConstructor(clazz) !=
      null;

      if (!hasDefaultConstructor && !hasContextConstructor) {
        logger.warn(
          "Test suite class '{}' has no suitable constructor",
          className
        );
        return false;
      }

      logger.debug("Test suite class '{}' validation passed", className);
      return true;
    } catch (TestSuiteInstantiationException e) {
      logger.warn("Test suite class '{}' validation failed: {}", className, e.getMessage());
      return false;
    }
  }

  /**
   * Checks if the class has a default (no-argument) constructor.
   *
   * @param clazz The class to check
   * @return true if a default constructor exists
   */
  private boolean hasDefaultConstructor(Class<?> clazz) {
    try {
      clazz.getDeclaredConstructor();
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * Exception thrown when test suite instantiation fails.
   */
  public static class TestSuiteInstantiationException extends RuntimeException {

    public TestSuiteInstantiationException(String message) {
      super(message);
    }

    public TestSuiteInstantiationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}