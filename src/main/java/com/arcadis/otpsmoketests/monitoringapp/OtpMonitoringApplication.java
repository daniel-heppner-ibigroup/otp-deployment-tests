package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.tests.HopeLinkTestSuite;
import com.arcadis.otpsmoketests.tests.SoundTransitTestSuite;
import io.javalin.Javalin;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import it.sauronsoftware.cron4j.Scheduler;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpMonitoringApplication {

  private static final Logger logger = LoggerFactory.getLogger(
    OtpMonitoringApplication.class
  );

  public static void main(String[] args) {
    try {
      // Create Micrometer registry for Prometheus
      PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT
      );

      TestRunner testRunner = new TestRunner(meterRegistry);

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
  private final Launcher launcher;
  private final Map<Class<?>, String> testSuites;

  // Fields to store the results of the most recent test run
  private final AtomicLong lastRunTestsFound = new AtomicLong(0);
  private final AtomicLong lastRunTestsFailed = new AtomicLong(0);
  private final AtomicLong lastRunDurationMs = new AtomicLong(0);

  // Fields to store the results of the most recent run for each suite
  private final Map<String, AtomicLong> lastRunSuiteTestsFound = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastRunSuiteTestsFailed = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastRunSuiteDurationMs = new ConcurrentHashMap<>();

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

    // Add timer for overall test execution
    Timer
      .builder("otp.tests.duration")
      .description("Time taken to execute OTP tests")
      .register(meterRegistry);

    // Add timer specifically for OTP plan requests
    Timer
      .builder("otp.plan.requests.duration")
      .description("Time taken for all OTP plan requests in test suite")
      .register(meterRegistry);

    // Add counter for plan requests
    Counter
      .builder("otp.plan.requests.total")
      .description("Total number of OTP plan requests made")
      .register(meterRegistry);

    // Add counter for plan request failures
    Counter
      .builder("otp.plan.requests.failures")
      .description("Number of failed OTP plan requests")
      .register(meterRegistry);

    // Add gauges for the most recent test run results
    Gauge
      .builder("otp.tests.last_run.total", lastRunTestsFound, AtomicLong::get)
      .description("Total number of tests found in the most recent test run")
      .register(meterRegistry);

    Gauge
      .builder(
        "otp.tests.last_run.failures",
        lastRunTestsFailed,
        AtomicLong::get
      )
      .description("Total number of tests failed in the most recent test run")
      .register(meterRegistry);

    Gauge
      .builder(
        "otp.tests.last_run.duration_ms",
        lastRunDurationMs,
        AtomicLong::get
      )
      .description("Duration (in milliseconds) of the most recent test run")
      .register(meterRegistry);

    // Add test suites
    addTestSuite(SoundTransitTestSuite.class, "SoundTransit");
//    addTestSuite(HopeLinkTestSuite.class, "Hopelink");
  }

  private void addTestSuite(Class<?> clazz, String name) {
    testSuites.put(clazz, name);

    // Initialize last run metrics for the suite
    lastRunSuiteTestsFound.put(name, new AtomicLong(0));
    lastRunSuiteTestsFailed.put(name, new AtomicLong(0));
    lastRunSuiteDurationMs.put(name, new AtomicLong(0));

    // Add suite-specific metrics
    Counter
      .builder(String.format("otp.tests.%s.total", name))
      .description(String.format("Total number of %s OTP tests run", name))
      .register(meterRegistry);

    Counter
      .builder(String.format("otp.tests.%s.failures", name))
      .description(String.format("Total number of %s OTP test failures", name))
      .register(meterRegistry);

    Timer
      .builder(MetricNames.planRequestTimer(name))
      .description(String.format("Time taken for %s OTP plan requests", name))
      .register(meterRegistry);

    // Add suite-specific last run gauges
    Gauge
      .builder(
        String.format("otp.tests.%s.last_run.total", name),
        () -> lastRunSuiteTestsFound.get(name).get()
      )
      .description(
        String.format("Total tests found in the last run of %s", name)
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format("otp.tests.%s.last_run.failures", name),
        () -> lastRunSuiteTestsFailed.get(name).get()
      )
      .description(
        String.format("Total tests failed in the last run of %s", name)
      )
      .register(meterRegistry);
    Gauge
      .builder(
        String.format("otp.tests.%s.last_run.duration_ms", name),
        () -> lastRunSuiteDurationMs.get(name).get()
      )
      .description(String.format("Duration (ms) of the last run of %s", name))
      .register(meterRegistry);

    // Add test method discovery to create test-specific timers
    try {
      for (var method : clazz.getMethods()) {
        if (method.isAnnotationPresent(Test.class)) {
          Timer
            .builder(MetricNames.planRequestTimer(name, method.getName()))
            .description(
              MetricNames.planRequestDescription(name, method.getName())
            )
            .register(meterRegistry);
        }
      }
    } catch (Exception e) {
      logger.warn("Failed to register test-specific timers for " + name, e);
    }
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
      long suiteDurationNanos = suiteSample.stop(
        meterRegistry.timer(String.format("otp.tests.%s.duration", suiteName))
      );

      TestExecutionSummary summary = listener.getSummary();
      suiteResults.put(suiteName, summary);

      // Record suite-specific metrics
      meterRegistry
        .counter("otp.tests." + suiteName + ".total")
        .increment(summary.getTestsFoundCount());
      meterRegistry
        .counter("otp.tests." + suiteName + ".failures")
        .increment(summary.getTestsFailedCount());

      // Update suite-specific last run metrics
      lastRunSuiteTestsFound.get(suiteName).set(summary.getTestsFoundCount());
      lastRunSuiteTestsFailed.get(suiteName).set(summary.getTestsFailedCount());
      lastRunSuiteDurationMs
        .get(suiteName)
        .set(TimeUnit.NANOSECONDS.toMillis(suiteDurationNanos));

      // Collect failures for this suite
      summary
        .getFailures()
        .forEach(failure -> {
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
          failureDetails.put(
            "errorMessage",
            failure.getException().getMessage()
          );
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
              "suite",
              suiteName,
              "class",
              failureDetails.get("testClass").toString(),
              "testName",
              failureDetails.get("testName").toString()
            )
            .increment();
        });
    }

    long totalDuration = totalSample.stop(
      meterRegistry.timer("otp.tests.duration")
    );
    Instant endTime = Instant.now();

    // Calculate total metrics across all suites
    long totalTestsFound = suiteResults
      .values()
      .stream()
      .mapToLong(TestExecutionSummary::getTestsFoundCount)
      .sum();
    long totalTestsSucceeded = suiteResults
      .values()
      .stream()
      .mapToLong(TestExecutionSummary::getTestsSucceededCount)
      .sum();
    long totalTestsFailed = suiteResults
      .values()
      .stream()
      .mapToLong(TestExecutionSummary::getTestsFailedCount)
      .sum();
    long totalTestsSkipped = suiteResults
      .values()
      .stream()
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

    // Update last run metrics
    this.lastRunTestsFound.set(totalTestsFound);
    this.lastRunTestsFailed.set(totalTestsFailed);
    this.lastRunDurationMs.set(TimeUnit.NANOSECONDS.toMillis(totalDuration));

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
