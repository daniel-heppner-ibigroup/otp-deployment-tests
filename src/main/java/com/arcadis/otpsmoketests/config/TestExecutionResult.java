package com.arcadis.otpsmoketests.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of executing a test suite.
 * Contains detailed information about test execution including
 * success/failure counts, timing, and error details.
 */
public class TestExecutionResult {

  private String deploymentName;
  private String testSuiteName;
  private boolean success;
  private int testsRun;
  private int successCount;
  private int failureCount;
  private int skippedCount;
  private long executionTimeMs;
  private LocalDateTime executionTime;
  private List<String> failureMessages;
  private Throwable executionException;

  /**
   * Creates a new TestExecutionResult with default values.
   */
  public TestExecutionResult() {
    this.executionTime = LocalDateTime.now();
    this.failureMessages = new ArrayList<>();
  }

  /**
   * Creates a TestExecutionResult for a failed execution.
   *
   * @param deploymentName The deployment name
   * @param testSuiteName The test suite name
   * @param exception The exception that caused the failure
   * @param executionTimeMs The execution time in milliseconds
   * @return A TestExecutionResult representing a failed execution
   */
  public static TestExecutionResult failure(
    String deploymentName,
    String testSuiteName,
    Throwable exception,
    long executionTimeMs
  ) {
    TestExecutionResult result = new TestExecutionResult();
    result.setDeploymentName(deploymentName);
    result.setTestSuiteName(testSuiteName);
    result.setSuccess(false);
    result.setTestsRun(0);
    result.setSuccessCount(0);
    result.setFailureCount(1);
    result.setSkippedCount(0);
    result.setExecutionTimeMs(executionTimeMs);
    result.setExecutionException(exception);
    result.getFailureMessages().add(exception.getMessage());
    return result;
  }

  /**
   * Creates a TestExecutionResult for a successful execution.
   *
   * @param deploymentName The deployment name
   * @param testSuiteName The test suite name
   * @param testsRun The number of tests run
   * @param executionTimeMs The execution time in milliseconds
   * @return A TestExecutionResult representing a successful execution
   */
  public static TestExecutionResult success(
    String deploymentName,
    String testSuiteName,
    int testsRun,
    long executionTimeMs
  ) {
    TestExecutionResult result = new TestExecutionResult();
    result.setDeploymentName(deploymentName);
    result.setTestSuiteName(testSuiteName);
    result.setSuccess(true);
    result.setTestsRun(testsRun);
    result.setSuccessCount(testsRun);
    result.setFailureCount(0);
    result.setSkippedCount(0);
    result.setExecutionTimeMs(executionTimeMs);
    return result;
  }

  // Getters and Setters

  public String getDeploymentName() {
    return deploymentName;
  }

  public void setDeploymentName(String deploymentName) {
    this.deploymentName = deploymentName;
  }

  public String getTestSuiteName() {
    return testSuiteName;
  }

  public void setTestSuiteName(String testSuiteName) {
    this.testSuiteName = testSuiteName;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public int getTestsRun() {
    return testsRun;
  }

  public void setTestsRun(int testsRun) {
    this.testsRun = testsRun;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public void setSuccessCount(int successCount) {
    this.successCount = successCount;
  }

  public int getFailureCount() {
    return failureCount;
  }

  public void setFailureCount(int failureCount) {
    this.failureCount = failureCount;
  }

  public int getSkippedCount() {
    return skippedCount;
  }

  public void setSkippedCount(int skippedCount) {
    this.skippedCount = skippedCount;
  }

  public long getExecutionTimeMs() {
    return executionTimeMs;
  }

  public void setExecutionTimeMs(long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
  }

  public LocalDateTime getExecutionTime() {
    return executionTime;
  }

  public void setExecutionTime(LocalDateTime executionTime) {
    this.executionTime = executionTime;
  }

  public List<String> getFailureMessages() {
    return failureMessages;
  }

  public void setFailureMessages(List<String> failureMessages) {
    this.failureMessages =
      failureMessages != null ? failureMessages : new ArrayList<>();
  }

  public Throwable getExecutionException() {
    return executionException;
  }

  public void setExecutionException(Throwable executionException) {
    this.executionException = executionException;
  }

  /**
   * Gets the success rate as a percentage.
   *
   * @return Success rate (0.0 to 1.0)
   */
  public double getSuccessRate() {
    if (testsRun == 0) {
      return success ? 1.0 : 0.0;
    }
    return (double) successCount / testsRun;
  }

  /**
   * Checks if there were any test failures.
   *
   * @return true if there were failures
   */
  public boolean hasFailures() {
    return failureCount > 0 || executionException != null;
  }

  /**
   * Gets a summary string of the execution result.
   *
   * @return A formatted summary string
   */
  public String getSummary() {
    if (executionException != null) {
      return String.format(
        "Execution failed: %s",
        executionException.getMessage()
      );
    }

    return String.format(
      "%d tests run, %d passed, %d failed, %d skipped in %dms",
      testsRun,
      successCount,
      failureCount,
      skippedCount,
      executionTimeMs
    );
  }

  /**
   * Gets a display name for this result.
   *
   * @return A formatted display name
   */
  public String getDisplayName() {
    return String.format("%s on %s", testSuiteName, deploymentName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestExecutionResult that = (TestExecutionResult) o;
    return (
      success == that.success &&
      testsRun == that.testsRun &&
      successCount == that.successCount &&
      failureCount == that.failureCount &&
      skippedCount == that.skippedCount &&
      executionTimeMs == that.executionTimeMs &&
      Objects.equals(deploymentName, that.deploymentName) &&
      Objects.equals(testSuiteName, that.testSuiteName) &&
      Objects.equals(executionTime, that.executionTime)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      deploymentName,
      testSuiteName,
      success,
      testsRun,
      successCount,
      failureCount,
      skippedCount,
      executionTimeMs,
      executionTime
    );
  }

  @Override
  public String toString() {
    return (
      "TestExecutionResult{" +
      "deploymentName='" +
      deploymentName +
      '\'' +
      ", testSuiteName='" +
      testSuiteName +
      '\'' +
      ", success=" +
      success +
      ", testsRun=" +
      testsRun +
      ", successCount=" +
      successCount +
      ", failureCount=" +
      failureCount +
      ", skippedCount=" +
      skippedCount +
      ", executionTimeMs=" +
      executionTimeMs +
      ", executionTime=" +
      executionTime +
      '}'
    );
  }
}
