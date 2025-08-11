package com.arcadis.otpsmoketests.config;

import java.util.Objects;

/**
 * Runtime context information for a specific deployment.
 * This class encapsulates deployment-specific information that test suites
 * need to execute against a particular OTP instance.
 */
public class DeploymentContext {

  private final String deploymentName;
  private final String otpUrl;

  /**
   * Creates a new DeploymentContext.
   *
   * @param deploymentName The name of the deployment (e.g., "SoundTransit", "Hopelink")
   * @param otpUrl The base URL for the OTP instance
   * @throws IllegalArgumentException if deploymentName or otpUrl is null or empty
   */
  public DeploymentContext(String deploymentName, String otpUrl) {
    if (deploymentName == null || deploymentName.trim().isEmpty()) {
      throw new IllegalArgumentException(
        "Deployment name cannot be null or empty"
      );
    }
    if (otpUrl == null || otpUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("OTP URL cannot be null or empty");
    }

    this.deploymentName = deploymentName.trim();
    this.otpUrl = otpUrl.trim();
  }

  /**
   * Gets the deployment name.
   *
   * @return The deployment name
   */
  public String getDeploymentName() {
    return deploymentName;
  }

  /**
   * Gets the OTP URL.
   *
   * @return The OTP base URL
   */
  public String getOtpUrl() {
    return otpUrl;
  }

  /**
   * Creates a display name for this deployment context.
   * Useful for logging and metrics.
   *
   * @return A formatted display name
   */
  public String getDisplayName() {
    return String.format("%s (%s)", deploymentName, otpUrl);
  }

  /**
   * Checks if this deployment context represents the same deployment as another.
   *
   * @param other The other deployment context to compare
   * @return true if both contexts represent the same deployment
   */
  public boolean isSameDeployment(DeploymentContext other) {
    if (other == null) {
      return false;
    }
    return (
      Objects.equals(this.deploymentName, other.deploymentName) &&
      Objects.equals(this.otpUrl, other.otpUrl)
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DeploymentContext that = (DeploymentContext) o;
    return (
      Objects.equals(deploymentName, that.deploymentName) &&
      Objects.equals(otpUrl, that.otpUrl)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(deploymentName, otpUrl);
  }

  @Override
  public String toString() {
    return (
      "DeploymentContext{" +
      "deploymentName='" +
      deploymentName +
      '\'' +
      ", otpUrl='" +
      otpUrl +
      '\'' +
      '}'
    );
  }
}
