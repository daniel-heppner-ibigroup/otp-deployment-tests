package com.arcadis.otpsmoketests.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.stereotype.Component;

/**
 * Manages metrics collection with deployment-specific tagging.
 * This class provides methods for recording test execution results, timing,
 * and failures with proper deployment and test suite context.
 */
@Component
public class DeploymentMetricsManager {

  private final MeterRegistry meterRegistry;

  public DeploymentMetricsManager(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Records test execution results with deployment and test suite tags.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param summary The test execution summary
   */
  public void recordTestExecution(
    String deploymentName,
    String testSuiteName,
    TestExecutionSummary summary
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);

    // Record total tests executed
    Counter.builder("otp.tests.total")
      .description("Total number of tests executed")
      .tags(tags)
      .register(meterRegistry)
      .increment(summary.getTestsFoundCount());

    // Record successful tests
    Counter.builder("otp.tests.successful")
      .description("Number of successful tests")
      .tags(tags)
      .register(meterRegistry)
      .increment(summary.getTestsSucceededCount());

    // Record failed tests
    Counter.builder("otp.tests.failed")
      .description("Number of failed tests")
      .tags(tags)
      .register(meterRegistry)
      .increment(summary.getTestsFailedCount());

    // Record skipped tests
    Counter.builder("otp.tests.skipped")
      .description("Number of skipped tests")
      .tags(tags)
      .register(meterRegistry)
      .increment(summary.getTestsSkippedCount());

    // Record aborted tests
    Counter.builder("otp.tests.aborted")
      .description("Number of aborted tests")
      .tags(tags)
      .register(meterRegistry)
      .increment(summary.getTestsAbortedCount());
  }

  /**
   * Records a test failure with deployment context.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param testName The name of the specific test that failed
   * @param error The error that caused the failure
   */
  public void recordTestFailure(
    String deploymentName,
    String testSuiteName,
    String testName,
    Throwable error
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);
    tags.add(Tag.of("test_name", sanitizeTagValue(testName)));
    tags.add(Tag.of("error_type", error.getClass().getSimpleName()));

    Counter.builder("otp.tests.failures")
      .description("Individual test failures with error details")
      .tags(tags)
      .register(meterRegistry)
      .increment();
  }

  /**
   * Records test suite execution duration.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param duration The duration of the test suite execution
   */
  public void recordTestSuiteDuration(
    String deploymentName,
    String testSuiteName,
    Duration duration
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);

    Timer.builder("otp.tests.duration")
      .description("Test suite execution duration")
      .tags(tags)
      .register(meterRegistry)
      .record(duration);
  }

  /**
   * Starts a timer for test execution timing.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @return A Timer.Sample that can be stopped to record the duration
   */
  public Timer.Sample startTestTimer(
    String deploymentName,
    String testSuiteName
  ) {
    return Timer.start(meterRegistry);
  }

  /**
   * Stops a timer and records the duration with deployment tags.
   *
   * @param sample The timer sample to stop
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   */
  public void stopTestTimer(
    Timer.Sample sample,
    String deploymentName,
    String testSuiteName
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);

    Timer timer = Timer.builder("otp.tests.duration")
      .description("Test suite execution duration")
      .tags(tags)
      .register(meterRegistry);

    sample.stop(timer);
  }

  /**
   * Records OTP API request timing with deployment context.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param testName The name of the specific test
   * @param duration The duration of the API request
   */
  public void recordApiRequestDuration(
    String deploymentName,
    String testSuiteName,
    String testName,
    Duration duration
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);
    tags.add(Tag.of("test_name", sanitizeTagValue(testName)));

    Timer.builder("otp.plan.requests.duration")
      .description("OTP API request duration")
      .tags(tags)
      .register(meterRegistry)
      .record(duration);
  }

  /**
   * Starts a timer for OTP API request timing.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param testName The name of the specific test
   * @return A Timer.Sample that can be stopped to record the duration
   */
  public Timer.Sample startApiRequestTimer(
    String deploymentName,
    String testSuiteName,
    String testName
  ) {
    return Timer.start(meterRegistry);
  }

  /**
   * Stops an API request timer and records the duration with deployment tags.
   *
   * @param sample The timer sample to stop
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param testName The name of the specific test
   */
  public void stopApiRequestTimer(
    Timer.Sample sample,
    String deploymentName,
    String testSuiteName,
    String testName
  ) {
    List<Tag> tags = createBaseTags(deploymentName, testSuiteName);
    tags.add(Tag.of("test_name", sanitizeTagValue(testName)));

    Timer timer = Timer.builder("otp.plan.requests.duration")
      .description("OTP API request duration")
      .tags(tags)
      .register(meterRegistry);

    sample.stop(timer);
  }

  /**
   * Creates base tags for deployment and test suite identification.
   *
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @return A mutable list of tags
   */
  private List<Tag> createBaseTags(String deploymentName, String testSuiteName) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of("deployment", sanitizeTagValue(deploymentName)));
    tags.add(Tag.of("test_suite", sanitizeTagValue(testSuiteName)));
    return tags;
  }

  /**
   * Sanitizes tag values to ensure they are valid for Prometheus.
   * Replaces invalid characters with underscores and converts to lowercase.
   *
   * @param value The tag value to sanitize
   * @return A sanitized tag value
   */
  private String sanitizeTagValue(String value) {
    if (value == null) {
      return "unknown";
    }
    return value.toLowerCase()
      .replaceAll("[^a-zA-Z0-9_]", "_")
      .replaceAll("_+", "_")
      .replaceAll("^_|_$", "");
  }
}