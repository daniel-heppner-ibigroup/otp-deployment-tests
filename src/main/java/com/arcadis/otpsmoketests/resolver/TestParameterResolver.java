package com.arcadis.otpsmoketests.resolver;

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
    String parameterName = parameterContext.getParameter().getName();

    // Support String parameters named "baseUrl" or "deploymentName"
    return (
      parameterType == String.class &&
      (
        parameterName.equals("baseUrl") ||
        parameterName.equals("deploymentName")
      )
    );
  }

  @Override
  public Object resolveParameter(
    ParameterContext parameterContext,
    ExtensionContext extensionContext
  ) {
    String parameterName = parameterContext.getParameter().getName();

    if (parameterName.equals("baseUrl")) {
      // Try to get from system property first, then environment variable, then default
      return System.getProperty(
        "otp.baseUrl",
        System.getenv("OTP_BASE_URL") != null
          ? System.getenv("OTP_BASE_URL")
          : DEFAULT_BASE_URL
      );
    } else if (parameterName.equals("deploymentName")) {
      // Try to get from system property first, then environment variable, then default
      return System.getProperty(
        "otp.deploymentName",
        System.getenv("OTP_DEPLOYMENT_NAME") != null
          ? System.getenv("OTP_DEPLOYMENT_NAME")
          : DEFAULT_DEPLOYMENT_NAME
      );
    }

    throw new IllegalArgumentException(
      "Unsupported parameter: " + parameterName
    );
  }
}
