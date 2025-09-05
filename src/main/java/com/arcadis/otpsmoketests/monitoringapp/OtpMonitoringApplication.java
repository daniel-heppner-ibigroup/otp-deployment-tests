package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.configuration.Configuration;
import com.arcadis.otpsmoketests.configuration.ConfigurationLoader;
import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import io.javalin.Javalin;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import it.sauronsoftware.cron4j.Scheduler;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpMonitoringApplication {

  private static final Logger logger = LoggerFactory.getLogger(
    OtpMonitoringApplication.class
  );

  public static void main(String[] args) {
    try {
      // Load configuration
      String configPath = args.length > 0 ? args[0] : "config.kdl";
      logger.info("Loading configuration from: {}", configPath);

      Configuration config;
      try {
        config = ConfigurationLoader.loadFromFile(configPath);
        logger.info(
          "Configuration loaded successfully with {} deployments",
          config.getDeploymentsUnderTest().size()
        );
      } catch (Exception e) {
        logger.error("Failed to load configuration from: {}", configPath, e);
        System.exit(1);
        return;
      }

      // Create Micrometer registry for Prometheus
      PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT
      );

      // Set the meter registry for all test suites to use the same registry as the HTTP endpoint
      BaseTestSuite.setMeterRegistry(meterRegistry);

      TestRunner testRunner = new TestRunner(meterRegistry, config);

      // Start Javalin HTTP server
      Javalin app = Javalin.create().start(8080);
      app.get("/run-tests", ctx -> ctx.json(testRunner.runTestsManually()));
      app.get("/health", ctx -> ctx.json(Map.of("status", "UP")));
      app.get(
        "/metrics",
        ctx -> ctx.result(meterRegistry.scrape()).contentType("text/plain")
      );

      // Start cron scheduler
      Scheduler scheduler = new Scheduler();
      scheduler.schedule("*/10 * * * *", testRunner::runScheduledTests);
      scheduler.start();

      logger.info(
        "OTP Monitoring Application started on port 8080, metrics at /metrics"
      );

      // Add shutdown hook
      Runtime
        .getRuntime()
        .addShutdownHook(
          new Thread(() -> {
            logger.info("Shutting down...");
            app.stop();
            scheduler.stop();
          })
        );
    } catch (Exception e) {
      logger.error("Failed to start application", e);
      System.exit(1);
    }
  }
}

class TestRunner {

  private static final Logger logger = LoggerFactory.getLogger(
    TestRunner.class
  );
  private final MeterRegistry meterRegistry;
  private final Map<Class<BaseTestSuite>, String> testSuites;
  private final Configuration configuration;

  // Fields to store the results of the most recent run for each suite
  private final Map<String, AtomicLong> lastRunSuiteTestsFound = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastRunSuiteTestsFailed = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastRunSuiteDurationMs = new ConcurrentHashMap<>();

  public TestRunner(MeterRegistry meterRegistry, Configuration configuration) {
    this.meterRegistry = meterRegistry;
    this.configuration = configuration;
    this.testSuites = new HashMap<>();

    // Add test suites from configuration
    for (Configuration.DeploymentUnderTest deployment : configuration.getDeploymentsUnderTest()) {
      for (Configuration.TestSuite testSuite : deployment.suites()) {
        addTestSuite(testSuite.clazz(), testSuite.name());
      }
    }
  }

  private void addTestSuite(Class<BaseTestSuite> clazz, String name) {
    testSuites.put(clazz, name);

    // Initialize last run metrics for the suite
    lastRunSuiteTestsFound.put(name, new AtomicLong(0));
    lastRunSuiteTestsFailed.put(name, new AtomicLong(0));
    lastRunSuiteDurationMs.put(name, new AtomicLong(0));

    // Add suite-specific last run gauges
    Gauge
      .builder(
        String.format("otp.tests.%s.tests_run", name),
        () -> lastRunSuiteTestsFound.get(name).get()
      )
      .description(
        String.format("Number of tests run in the last run of %s", name)
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format("otp.tests.%s.tests_failed", name),
        () -> lastRunSuiteTestsFailed.get(name).get()
      )
      .description(
        String.format("Number of tests failed in the last run of %s", name)
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format("otp.tests.%s.duration_ms", name),
        () -> lastRunSuiteDurationMs.get(name).get()
      )
      .description(String.format("Duration (ms) of the last run of %s", name))
      .register(meterRegistry);
  }

  public Map<String, Object> runTestsManually() {
    return executeTests();
  }

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

    List<Map<String, Object>> allFailures = new ArrayList<>();
    Map<String, CustomTestRunner.SuiteResult> suiteResults = new HashMap<>();

    // Run each test suite individually with custom URL
    for (Map.Entry<Class<BaseTestSuite>, String> suite : testSuites.entrySet()) {
      String suiteName = suite.getValue();
      Class<BaseTestSuite> suiteClass = suite.getKey();

      // Get the base URL for this suite from configuration
      String baseUrl = getBaseUrlForSuite(suiteName);

      // Run the test suite with the custom URL
      CustomTestRunner.SuiteResult suiteResult = CustomTestRunner.runTestSuite(
        suiteClass,
        suiteName,
        baseUrl
      );

      suiteResults.put(suiteName, suiteResult);

      // Update suite-specific last run metrics
      lastRunSuiteTestsFound
        .get(suiteName)
        .set(suiteResult.getTestsFoundCount());
      lastRunSuiteTestsFailed
        .get(suiteName)
        .set(suiteResult.getTestsFailedCount());
      lastRunSuiteDurationMs.get(suiteName).set(suiteResult.totalDurationMs());

      // Collect failures for this suite
      suiteResult
        .testResults()
        .stream()
        .filter(testResult -> !testResult.isPassed())
        .forEach(testResult -> {
          Map<String, Object> failureDetails = new HashMap<>();
          failureDetails.put("suite", suiteName);
          failureDetails.put("testClass", suiteClass.getSimpleName());
          failureDetails.put("testName", testResult.getTestName());
          failureDetails.put(
            "errorMessage",
            testResult.getException() != null
              ? testResult.getException().getMessage()
              : "Unknown error"
          );
          failureDetails.put(
            "stackTrace",
            testResult.getException() != null
              ? Arrays.toString(testResult.getException().getStackTrace())
              : ""
          );
          allFailures.add(failureDetails);

          logger.error(
            "Test failure occurred in suite: " + suiteName,
            StructuredArguments.kv("runId", runId),
            StructuredArguments.kv("failure", failureDetails)
          );
        });
    }

    Instant endTime = Instant.now();
    long totalDurationMs = java.time.Duration
      .between(startTime, endTime)
      .toMillis();

    // Calculate total metrics across all suites
    long totalTestsFound = suiteResults
      .values()
      .stream()
      .mapToLong(CustomTestRunner.SuiteResult::getTestsFoundCount)
      .sum();
    long totalTestsSucceeded = suiteResults
      .values()
      .stream()
      .mapToLong(CustomTestRunner.SuiteResult::getTestsSucceededCount)
      .sum();
    long totalTestsFailed = suiteResults
      .values()
      .stream()
      .mapToLong(CustomTestRunner.SuiteResult::getTestsFailedCount)
      .sum();
    long totalTestsSkipped = suiteResults
      .values()
      .stream()
      .mapToLong(CustomTestRunner.SuiteResult::getTestsSkippedCount)
      .sum();

    // Prepare result map
    Map<String, Object> result = new HashMap<>();
    result.put("runId", runId);
    result.put("startTime", startTime);
    result.put("endTime", endTime);
    result.put("durationMs", totalDurationMs);
    result.put("testsFound", totalTestsFound);
    result.put("testsSucceeded", totalTestsSucceeded);
    result.put("testsFailed", totalTestsFailed);
    result.put("testsSkipped", totalTestsSkipped);
    result.put("failures", allFailures);

    // Add per-suite results
    Map<String, Object> suiteStats = new HashMap<>();
    suiteResults.forEach((suiteName, suiteResult) -> {
      Map<String, Object> stats = new HashMap<>();
      stats.put("testsFound", suiteResult.getTestsFoundCount());
      stats.put("testsSucceeded", suiteResult.getTestsSucceededCount());
      stats.put("testsFailed", suiteResult.getTestsFailedCount());
      stats.put("testsSkipped", suiteResult.getTestsSkippedCount());
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

  private String getBaseUrlForSuite(String suiteName) {
    return configuration
      .getDeploymentsUnderTest()
      .stream()
      .filter(deployment ->
        deployment
          .suites()
          .stream()
          .anyMatch(suite -> suite.name().equals(suiteName))
      )
      .findFirst()
      .map(Configuration.DeploymentUnderTest::url)
      .orElse(null);
  }
}
