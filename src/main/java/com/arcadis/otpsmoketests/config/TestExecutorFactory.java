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
   * @param geocodingConfig The geocoding configuration
   * @return A TestSuiteExecutor instance
   * @throws TestSuiteInstantiationException if the test suite cannot be created
   */
  public TestSuiteExecutor createExecutor(
    String deploymentName,
    DeploymentConfiguration.DeploymentConfig deployment,
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig,
    DeploymentConfiguration.GeocodingConfig geocodingConfig
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

    Class<?> testSuiteClass = loadTestSuiteClass(
      testSuiteConfig.getClassName()
    );

    return new TestSuiteExecutor(
      deploymentName,
      testSuiteClass,
      deploymentContext,
      testSuiteConfig,
      geocodingConfig
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
          String.format("Class '%s' does not extend BaseTestSuite", className)
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
   * Creates an instance of the test suite class with deployment context and geocoding configuration.
   * This method handles the current constructor pattern that requires DeploymentContext, 
   * peliasBaseUrl, latitude, and longitude parameters.
   *
   * @param testSuiteClass The test suite class to instantiate
   * @param deploymentContext The deployment context
   * @param geocodingConfig The geocoding configuration
   * @return An instance of the test suite
   * @throws TestSuiteInstantiationException if the test suite cannot be instantiated
   */
  public Object createTestSuiteInstance(
    Class<?> testSuiteClass,
    DeploymentContext deploymentContext,
    DeploymentConfiguration.GeocodingConfig geocodingConfig
  ) {
    logger.debug(
      "Creating instance of test suite class: {}",
      testSuiteClass.getName()
    );

    try {
      // Try to find the constructor that accepts DeploymentContext, String, double, double
      Constructor<?> constructor = testSuiteClass.getDeclaredConstructor(
        DeploymentContext.class,
        String.class,
        double.class,
        double.class
      );
      
      logger.debug(
        "Using deployment context constructor for {} with geocoding parameters",
        testSuiteClass.getName()
      );
      
      return constructor.newInstance(
        deploymentContext,
        geocodingConfig.getPeliasBaseUrl(),
        geocodingConfig.getFocusLatitude(),
        geocodingConfig.getFocusLongitude()
      );
    } catch (
      NoSuchMethodException
      | InstantiationException
      | IllegalAccessException
      | InvocationTargetException e
    ) {
      String message = String.format(
        "Failed to instantiate test suite class: %s. Expected constructor with parameters: (DeploymentContext, String, double, double)",
        testSuiteClass.getName()
      );
      logger.error(message, e);
      throw new TestSuiteInstantiationException(message, e);
    }
  }

  /**
   * Checks if the class has the required constructor for test suite instantiation.
   *
   * @param clazz The class to check
   * @return true if the required constructor exists
   */
  private boolean hasRequiredConstructor(Class<?> clazz) {
    try {
      clazz.getDeclaredConstructor(
        DeploymentContext.class,
        String.class,
        double.class,
        double.class
      );
      return true;
    } catch (NoSuchMethodException e) {
      return false;
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

      // Check if we can find the required constructor
      boolean hasRequiredConstructor = hasRequiredConstructor(clazz);

      if (!hasRequiredConstructor) {
        logger.warn(
          "Test suite class '{}' does not have required constructor (DeploymentContext, String, double, double)",
          className
        );
        return false;
      }

      logger.debug("Test suite class '{}' validation passed", className);
      return true;
    } catch (TestSuiteInstantiationException e) {
      logger.warn(
        "Test suite class '{}' validation failed: {}",
        className,
        e.getMessage()
      );
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
