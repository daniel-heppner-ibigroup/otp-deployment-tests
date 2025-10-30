package com.arcadis.otpsmoketests.tests;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * ParameterResolver for providing baseUrl and deploymentName parameters
 * when running JUnit tests directly in an IDE during development.
 *
 * This resolver provides default values that allow tests to run without
 * requiring the CustomTestRunner infrastructure.
 */
public class TestParameterResolver implements ParameterResolver {

  private static final String DEFAULT_BASE_URL = "http://localhost:8080";
  private static final String DEFAULT_DEPLOYMENT_NAME = "local-development";

  @Override
  public boolean supportsParameter(
    ParameterContext parameterContext,
    ExtensionContext extensionContext
  ) {
    Class<?> parameterType = parameterContext.getParameter().getType();
    int parameterIndex = parameterContext.getIndex();

    // Support first two String parameters (baseUrl, deploymentName)
    return parameterType == String.class && parameterIndex < 2;
  }

  @Override
  public Object resolveParameter(
    ParameterContext parameterContext,
    ExtensionContext extensionContext
  ) {
    int parameterIndex = parameterContext.getIndex();

    if (parameterIndex == 0) {
      // First parameter is baseUrl
      return System.getProperty(
        "baseUrl",
        System.getenv("BASE_URL") != null
          ? System.getenv("BASE_URL")
          : DEFAULT_BASE_URL
      );
    } else if (parameterIndex == 1) {
      // Second parameter is deploymentName
      return System.getProperty(
        "deploymentName",
        System.getenv("DEPLOYMENT_NAME") != null
          ? System.getenv("DEPLOYMENT_NAME")
          : DEFAULT_DEPLOYMENT_NAME
      );
    }

    throw new IllegalArgumentException(
      "Unsupported parameter index: " + parameterIndex
    );
  }
}
