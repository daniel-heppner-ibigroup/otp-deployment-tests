package com.arcadis.otp_deployment_tests;

import com.arcadis.otp_deployment_tests.tests.HopeLinkSmokeTest;
import com.arcadis.otp_deployment_tests.tests.SoundTransitSmokeTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.*;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class OtpMonitoringApplication {

  public static void main(String[] args) {
    SpringApplication.run(OtpMonitoringApplication.class, args);
  }
}

@RestController
@Component
class TestRunner {

  private static final Logger logger = LoggerFactory.getLogger(
    TestRunner.class
  );
  private final MeterRegistry meterRegistry;
  private final Launcher launcher;
  private final SummaryGeneratingListener listener;

  public TestRunner(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.launcher = LauncherFactory.create();
    this.listener = new SummaryGeneratingListener();

    // Initialize base metrics
    Counter
      .builder("otp.tests.total")
      .description("Total number of OTP tests run")
      .register(meterRegistry);

    Counter
      .builder("otp.tests.failures")
      .description("Total number of OTP test failures")
      .register(meterRegistry);

    Timer
      .builder("otp.tests.duration")
      .description("Time taken to execute OTP tests")
      .register(meterRegistry);
  }

  @GetMapping("/run-tests")
  public Map<String, Object> runTestsManually() {
    return executeTests();
  }

  @Scheduled(cron = "0 0 * * * *") // Run every hour
  public void runScheduledTests() {
    executeTests();
  }

  private Map<String, Object> executeTests() {
    String runId = UUID.randomUUID().toString();
    Instant startTime = Instant.now();

    logger.info(
      "Starting test execution",
      StructuredArguments.kv("runId", runId),
      StructuredArguments.kv("startTime", startTime)
    );

    LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
      .request()
      .selectors(DiscoverySelectors.selectClass(SoundTransitSmokeTest.class))
      .selectors(DiscoverySelectors.selectClass(HopeLinkSmokeTest.class))
      .build();

    launcher.registerTestExecutionListeners(listener);

    Timer.Sample sample = Timer.start(meterRegistry);
    launcher.execute(request);

    TestExecutionSummary summary = listener.getSummary();
    long duration = sample.stop(meterRegistry.timer("otp.tests.duration"));
    Instant endTime = Instant.now();

    // Record test execution metrics
    meterRegistry
      .counter("otp.tests.total")
      .increment(summary.getTestsFoundCount());
    meterRegistry
      .counter("otp.tests.failures")
      .increment(summary.getTestsFailedCount());

    // Log detailed test results
    Map<String, Object> logContext = new HashMap<>();
    logContext.put("runId", runId);
    logContext.put("startTime", startTime);
    logContext.put("endTime", endTime);
    logContext.put("durationMs", duration);
    logContext.put("testsFound", summary.getTestsFoundCount());
    logContext.put("testsSucceeded", summary.getTestsSucceededCount());
    logContext.put("testsFailed", summary.getTestsFailedCount());
    logContext.put("testsSkipped", summary.getTestsSkippedCount());

    // Record individual test failures with details
    List<Map<String, Object>> failures = new ArrayList<>();
    summary
      .getFailures()
      .forEach(failure -> {
        Map<String, Object> failureDetails = new HashMap<>();
        failureDetails.put(
          "testClass",
          failure
            .getTestIdentifier()
            .getSource()
            .map(source -> source.toString().split("\\[")[0])
            .orElse("unknown")
        );
        failureDetails.put(
          "testName",
          failure.getTestIdentifier().getDisplayName()
        );
        failureDetails.put("errorMessage", failure.getException().getMessage());
        failureDetails.put(
          "stackTrace",
          Arrays.toString(failure.getException().getStackTrace())
        );
        failures.add(failureDetails);

        // Log each failure separately for easier querying
        logger.error(
          "Test failure occurred",
          StructuredArguments.kv("runId", runId),
          StructuredArguments.kv("failure", failureDetails)
        );

        meterRegistry
          .counter(
            "otp.test.failure",
            "class",
            failureDetails.get("testClass").toString(),
            "testName",
            failureDetails.get("testName").toString()
          )
          .increment();
      });

    logContext.put("failures", failures);

    // Log complete test summary
    if (summary.getTestsFailedCount() > 0) {
      logger.error(
        "Test execution completed with failures",
        StructuredArguments.entries(logContext)
      );
    } else {
      logger.info(
        "Test execution completed successfully",
        StructuredArguments.entries(logContext)
      );
    }

    // Return summary for API endpoint
    Map<String, Object> result = new HashMap<>();
    result.put("runId", runId);
    result.put("startTime", startTime);
    result.put("endTime", endTime);
    result.put("durationMs", duration);
    result.put("testsFound", summary.getTestsFoundCount());
    result.put("testsSucceeded", summary.getTestsSucceededCount());
    result.put("testsFailed", summary.getTestsFailedCount());
    result.put("testsSkipped", summary.getTestsSkippedCount());
    result.put("failures", failures);
    return result;
  }
}
