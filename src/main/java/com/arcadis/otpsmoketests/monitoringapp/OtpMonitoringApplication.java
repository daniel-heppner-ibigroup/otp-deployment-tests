package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.configuration.Configuration;
import com.arcadis.otpsmoketests.configuration.ConfigurationLoader;
import com.arcadis.otpsmoketests.reporting.HtmlResultsGenerator;
import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
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

      // Configure ObjectMapper with JSR310 module for Java 8 time support
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.registerModule(new JavaTimeModule());

      // Start Javalin HTTP server with custom ObjectMapper
      Javalin app = Javalin
        .create(jConfig -> {
          jConfig.jsonMapper(new JavalinJackson());
        })
        .start(8080);
      app.get("/run-tests", ctx -> ctx.json(testRunner.runTestsManually()));
      app.get("/health", ctx -> ctx.json(Map.of("status", "UP")));
      app.get(
        "/metrics",
        ctx -> ctx.result(meterRegistry.scrape()).contentType("text/plain")
      );

      // Start cron scheduler with individual suite intervals
      Scheduler scheduler = new Scheduler();
      testRunner.scheduleTestSuites(scheduler);
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
  private final Map<String, TestSuiteConfig> testSuites;
  private final Configuration configuration;

  public record TestSuiteConfig(
    Class<BaseTestSuite> clazz,
    String suiteName,
    String deploymentName,
    String interval,
    String baseUrl
  ) {}

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
        addTestSuite(
          testSuite.clazz(),
          testSuite.name(),
          deployment.name(),
          testSuite.interval(),
          deployment.url()
        );
      }
    }
  }

  private void addTestSuite(
    Class<BaseTestSuite> clazz,
    String suiteName,
    String deploymentName,
    String interval,
    String baseUrl
  ) {
    String testSuiteKey = deploymentName + "." + suiteName;
    TestSuiteConfig config = new TestSuiteConfig(
      clazz,
      suiteName,
      deploymentName,
      interval,
      baseUrl
    );
    testSuites.put(testSuiteKey, config);

    // Initialize last run metrics for the suite with deployment.suite naming
    lastRunSuiteTestsFound.put(testSuiteKey, new AtomicLong(0));
    lastRunSuiteTestsFailed.put(testSuiteKey, new AtomicLong(0));
    lastRunSuiteDurationMs.put(testSuiteKey, new AtomicLong(0));

    // Add suite-specific last run gauges with deployment.suite naming
    Gauge
      .builder(
        String.format("otp.tests.%s.%s.tests_run", deploymentName, suiteName),
        () -> lastRunSuiteTestsFound.get(testSuiteKey).get()
      )
      .description(
        String.format(
          "Number of tests run in the last run of %s.%s",
          deploymentName,
          suiteName
        )
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format(
          "otp.tests.%s.%s.tests_failed",
          deploymentName,
          suiteName
        ),
        () -> lastRunSuiteTestsFailed.get(testSuiteKey).get()
      )
      .description(
        String.format(
          "Number of tests failed in the last run of %s.%s",
          deploymentName,
          suiteName
        )
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format("otp.tests.%s.%s.duration_ms", deploymentName, suiteName),
        () -> lastRunSuiteDurationMs.get(testSuiteKey).get()
      )
      .description(
        String.format(
          "Duration (ms) of the last run of %s.%s",
          deploymentName,
          suiteName
        )
      )
      .register(meterRegistry);
  }

  public Map<String, Object> runTestsManually() {
    return executeTests();
  }

  public void scheduleTestSuites(Scheduler scheduler) {
    // Schedule each test suite based on its configured interval
    for (Map.Entry<String, TestSuiteConfig> entry : testSuites.entrySet()) {
      String testSuiteKey = entry.getKey();
      TestSuiteConfig config = entry.getValue();

      // Create a runnable that executes just this specific test suite
      Runnable suiteRunner = () -> runSpecificTestSuite(testSuiteKey, config);

      // Schedule with the configured interval
      scheduler.schedule(config.interval(), suiteRunner);

      logger.info(
        "Scheduled test suite {} with interval: {}",
        testSuiteKey,
        config.interval()
      );
    }
  }

  private void runSpecificTestSuite(
    String testSuiteKey,
    TestSuiteConfig config
  ) {
    String runId = UUID.randomUUID().toString();
    Instant startTime = Instant.now();

    logger.info(
      "Starting scheduled test execution for suite: {}",
      testSuiteKey,
      StructuredArguments.kv("runId", runId)
    );

    // Run the test suite with the custom URL and deployment name
    CustomTestRunner.SuiteResult suiteResult = CustomTestRunner.runTestSuite(
      config.clazz(),
      config.suiteName(),
      config.baseUrl(),
      config.deploymentName()
    );

    // Update suite-specific last run metrics
    lastRunSuiteTestsFound
      .get(testSuiteKey)
      .set(suiteResult.getTestsFoundCount());
    lastRunSuiteTestsFailed
      .get(testSuiteKey)
      .set(suiteResult.getTestsFailedCount());
    lastRunSuiteDurationMs.get(testSuiteKey).set(suiteResult.totalDurationMs());

    // Generate HTML report
    try {
      HtmlResultsGenerator.TestSuiteReport report = new HtmlResultsGenerator.TestSuiteReport(
        config.deploymentName(),
        config.suiteName(),
        suiteResult,
        startTime
      );
      HtmlResultsGenerator.generateHtmlReport(report);
    } catch (Exception e) {
      logger.error(
        "Failed to generate HTML report for suite: {}",
        testSuiteKey,
        e
      );
    }

    // Log results
    if (suiteResult.getTestsFailedCount() > 0) {
      logger.error(
        "Scheduled test suite {} completed with {} failures",
        testSuiteKey,
        suiteResult.getTestsFailedCount(),
        StructuredArguments.kv("runId", runId)
      );
    } else {
      logger.info(
        "Scheduled test suite {} completed successfully",
        testSuiteKey,
        StructuredArguments.kv("runId", runId)
      );
    }
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
    for (Map.Entry<String, TestSuiteConfig> entry : testSuites.entrySet()) {
      String testSuiteKey = entry.getKey();
      TestSuiteConfig config = entry.getValue();

      // Run the test suite with the custom URL and deployment name
      CustomTestRunner.SuiteResult suiteResult = CustomTestRunner.runTestSuite(
        config.clazz(),
        config.suiteName(),
        config.baseUrl(),
        config.deploymentName()
      );

      suiteResults.put(testSuiteKey, suiteResult);

      // Update suite-specific last run metrics
      lastRunSuiteTestsFound
        .get(testSuiteKey)
        .set(suiteResult.getTestsFoundCount());
      lastRunSuiteTestsFailed
        .get(testSuiteKey)
        .set(suiteResult.getTestsFailedCount());
      lastRunSuiteDurationMs
        .get(testSuiteKey)
        .set(suiteResult.totalDurationMs());

      // Generate HTML report for this suite
      try {
        HtmlResultsGenerator.TestSuiteReport report = new HtmlResultsGenerator.TestSuiteReport(
          config.deploymentName(),
          config.suiteName(),
          suiteResult,
          startTime
        );
        HtmlResultsGenerator.generateHtmlReport(report);
      } catch (Exception e) {
        logger.error(
          "Failed to generate HTML report for suite: {}",
          testSuiteKey,
          e
        );
      }

      // Collect failures for this suite
      suiteResult
        .testResults()
        .stream()
        .filter(testResult -> !testResult.isPassed())
        .forEach(testResult -> {
          Map<String, Object> failureDetails = new HashMap<>();
          failureDetails.put("deployment", config.deploymentName());
          failureDetails.put("suite", config.suiteName());
          failureDetails.put("testClass", config.clazz().getSimpleName());
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
            "Test failure occurred in suite: " +
            config.deploymentName() +
            "." +
            config.suiteName(),
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
    suiteResults.forEach((testSuiteKey, suiteResult) -> {
      Map<String, Object> stats = new HashMap<>();
      stats.put("testsFound", suiteResult.getTestsFoundCount());
      stats.put("testsSucceeded", suiteResult.getTestsSucceededCount());
      stats.put("testsFailed", suiteResult.getTestsFailedCount());
      stats.put("testsSkipped", suiteResult.getTestsSkippedCount());
      suiteStats.put(testSuiteKey, stats);
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
