package com.arcadis.otpsmoketests.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for OTP deployment testing.
 * Supports multiple deployments with flexible test suite mapping and scheduling.
 */
@Component
@ConfigurationProperties(prefix = "otp")
@Validated
public class DeploymentConfiguration {

  @Valid
  @NotNull
  private Map<String, DeploymentConfig> deployments = new HashMap<>();

  public Map<String, DeploymentConfig> getDeployments() {
    return deployments;
  }

  public void setDeployments(Map<String, DeploymentConfig> deployments) {
    this.deployments = deployments;
  }

  /**
   * Configuration for a single OTP deployment.
   */
  public static class DeploymentConfig {

    @NotBlank(message = "Deployment name cannot be blank")
    private String name;

    @NotBlank(message = "OTP URL cannot be blank")
    @Pattern(
      regexp = "^https?://.*",
      message = "OTP URL must be a valid HTTP/HTTPS URL"
    )
    private String otpUrl;

    @Valid
    @NotNull
    private List<TestSuiteConfig> testSuites = new ArrayList<>();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getOtpUrl() {
      return otpUrl;
    }

    public void setOtpUrl(String otpUrl) {
      this.otpUrl = otpUrl;
    }

    public List<TestSuiteConfig> getTestSuites() {
      return testSuites;
    }

    public void setTestSuites(List<TestSuiteConfig> testSuites) {
      this.testSuites = testSuites;
    }
  }

  /**
   * Configuration for a test suite within a deployment.
   */
  public static class TestSuiteConfig {

    @NotBlank(message = "Test suite class name cannot be blank")
    private String className;

    @NotBlank(message = "Schedule expression cannot be blank")
    private String schedule;

    private boolean enabled = true;

    public String getClassName() {
      return className;
    }

    public void setClassName(String className) {
      this.className = className;
    }

    public String getSchedule() {
      return schedule;
    }

    public void setSchedule(String schedule) {
      this.schedule = schedule;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
