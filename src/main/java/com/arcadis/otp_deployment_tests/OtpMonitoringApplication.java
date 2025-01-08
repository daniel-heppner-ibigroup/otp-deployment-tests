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
  private final Map<Class<?>, String> testSuites;

  public TestRunner(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.launcher = LauncherFactory.create();
    this.testSuites = new HashMap<>();

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


    // Add test suites
    addTestSuite(SoundTransitSmokeTest.class, "SoundTransit");
    addTestSuite(HopeLinkSmokeTest.class, "Hopelink");
  }

  private void addTestSuite(Class<?> clazz, String name) {
    testSuites.put(clazz, name);
    Counter
      .builder(String.format("otp.tests.%s.total", name))
      .description(String.format("Total number of %s OTP tests run", name))
      .register(meterRegistry);
    Counter
      .builder(String.format("otp.tests.%s.failures", name))
      .description(String.format("Total number of %s OTP test failures", name))
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
    Timer.Sample totalSample = Timer.start(meterRegistry);
    
    logger.info(
      "Starting test execution",
      StructuredArguments.kv("runId", runId),
      StructuredArguments.kv("startTime", startTime)
    );

    List<Map<String, Object>> allFailures = new ArrayList<>();
    Map<String, TestExecutionSummary> suiteResults = new HashMap<>();
    
    // Run each test suite individually
    for (Map.Entry<Class<?>, String> suite : testSuites.entrySet()) {
      String suiteName = suite.getValue();
      Class<?> suiteClass = suite.getKey();
      
      LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
        .request()
        .selectors(DiscoverySelectors.selectClass(suiteClass))
        .build();

      SummaryGeneratingListener listener = new SummaryGeneratingListener();
      launcher.registerTestExecutionListeners(listener);
      
      Timer.Sample suiteSample = Timer.start(meterRegistry);
      launcher.execute(request);
      suiteSample.stop(meterRegistry.timer("otp.tests." + suiteName + ".duration"));
      
      TestExecutionSummary summary = listener.getSummary();
      suiteResults.put(suiteName, summary);
      
      // Record suite-specific metrics
      meterRegistry
        .counter("otp.tests." + suiteName + ".total")
        .increment(summary.getTestsFoundCount());
      meterRegistry
        .counter("otp.tests." + suiteName + ".failures")
        .increment(summary.getTestsFailedCount());
      
      // Collect failures for this suite
      summary.getFailures().forEach(failure -> {
        Map<String, Object> failureDetails = new HashMap<>();
        failureDetails.put("suite", suiteName);
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
        allFailures.add(failureDetails);

        logger.error(
          "Test failure occurred in suite: " + suiteName,
          StructuredArguments.kv("runId", runId),
          StructuredArguments.kv("failure", failureDetails)
        );

        meterRegistry
          .counter(
            "otp.test.failure",
            "suite", suiteName,
            "class", failureDetails.get("testClass").toString(),
            "testName", failureDetails.get("testName").toString()
          )
          .increment();
      });
    }

    long totalDuration = totalSample.stop(meterRegistry.timer("otp.tests.duration"));
    Instant endTime = Instant.now();

    // Calculate total metrics across all suites
    long totalTestsFound = suiteResults.values().stream()
      .mapToLong(TestExecutionSummary::getTestsFoundCount)
      .sum();
    long totalTestsSucceeded = suiteResults.values().stream()
      .mapToLong(TestExecutionSummary::getTestsSucceededCount)
      .sum();
    long totalTestsFailed = suiteResults.values().stream()
      .mapToLong(TestExecutionSummary::getTestsFailedCount)
      .sum();
    long totalTestsSkipped = suiteResults.values().stream()
      .mapToLong(TestExecutionSummary::getTestsSkippedCount)
      .sum();

    // Update global metrics
    meterRegistry.counter("otp.tests.total").increment(totalTestsFound);
    meterRegistry.counter("otp.tests.failures").increment(totalTestsFailed);

    // Prepare result map
    Map<String, Object> result = new HashMap<>();
    result.put("runId", runId);
    result.put("startTime", startTime);
    result.put("endTime", endTime);
    result.put("durationMs", totalDuration);
    result.put("testsFound", totalTestsFound);
    result.put("testsSucceeded", totalTestsSucceeded);
    result.put("testsFailed", totalTestsFailed);
    result.put("testsSkipped", totalTestsSkipped);
    result.put("failures", allFailures);
    
    // Add per-suite results
    Map<String, Object> suiteStats = new HashMap<>();
    suiteResults.forEach((suiteName, summary) -> {
      Map<String, Object> stats = new HashMap<>();
      stats.put("testsFound", summary.getTestsFoundCount());
      stats.put("testsSucceeded", summary.getTestsSucceededCount());
      stats.put("testsFailed", summary.getTestsFailedCount());
      stats.put("testsSkipped", summary.getTestsSkippedCount());
      suiteStats.put(suiteName, stats);
    });
    result.put("suiteResults", suiteStats);

    // Log complete test summary
    if (totalTestsFailed > 0) {
      logger.error(
        "Test execution completed with failures",
        StructuredArguments.entries(result)
      );
    } else {
      logger.info(
        "Test execution completed successfully",
        StructuredArguments.entries(result)
      );
    }

    return result;
  }
}
