package com.arcadis.otpsmoketests.runner;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.reporting.HtmlReportGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.opentripplanner.assertions.ItineraryAssertionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomTestRunner {

  private static final Logger logger = LoggerFactory.getLogger(
    CustomTestRunner.class
  );

  public static class TestResult {

    private final String testName;
    private final boolean passed;
    private final Throwable exception;
    private final long durationMs;

    public TestResult(
      String testName,
      boolean passed,
      ItineraryAssertionError exception,
      long durationMs
    ) {
      this.testName = testName;
      this.passed = passed;
      this.exception = exception;
      this.durationMs = durationMs;
    }

    public String getTestName() {
      return testName;
    }

    public boolean isPassed() {
      return passed;
    }

    public Throwable getException() {
      return exception;
    }

    public long getDurationMs() {
      return durationMs;
    }
  }

  public record SuiteResult(
    String suiteName,
    List<TestResult> testResults,
    long totalDurationMs
  ) {
    public long getTestsFoundCount() {
      return testResults.size();
    }

    public long getTestsSucceededCount() {
      return testResults.stream().mapToLong(t -> t.isPassed() ? 1 : 0).sum();
    }

    public long getTestsFailedCount() {
      return testResults.stream().mapToLong(t -> t.isPassed() ? 0 : 1).sum();
    }

    public long getTestsSkippedCount() {
      return 0;
    } // For simplicity, we don't handle skipped tests
  }

  public static SuiteResult runTestSuite(
    Class<? extends BaseTestSuite> suiteClass,
    String suiteName,
    String baseUrl,
    String deploymentName
  ) {
    List<TestResult> testResults = new ArrayList<>();
    long suiteStartTime = System.nanoTime();

    try {
      // Try constructor with baseUrl and deploymentName parameters first
      BaseTestSuite suiteInstance = getBaseTestSuite(
        suiteClass,
        baseUrl,
        deploymentName
      );

      // Find all @Test methods
      Method[] methods = suiteClass.getMethods();
      var filteredMethods = Arrays
        .stream(methods)
        .filter(m -> m.isAnnotationPresent(Test.class))
        .toList();
      for (Method method : filteredMethods) {
        String testName = method.getName();
        long testStartTime = System.nanoTime();

        try {
          logger.debug("Running test: {}.{}", suiteName, testName);
          method.invoke(suiteInstance);
          long testDuration = (System.nanoTime() - testStartTime) / 1_000_000;
          testResults.add(new TestResult(testName, true, null, testDuration));
          logger.debug("Test passed: {}.{}", suiteName, testName);
        } catch (InvocationTargetException e) {
          if (e.getTargetException() instanceof ItineraryAssertionError) {
            long testDuration = (System.nanoTime() - testStartTime) / 1_000_000;
            ItineraryAssertionError cause = (ItineraryAssertionError) e.getCause();
            testResults.add(
              new TestResult(testName, false, cause, testDuration)
            );
            logger.error("Test failed: {}.{}", suiteName, testName, cause);
          }
        }
      }
    } catch (Exception e) {
      logger.error("Failed to instantiate test suite: {}", suiteName, e);
    }

    long totalDuration = (System.nanoTime() - suiteStartTime) / 1_000_000;
    SuiteResult suiteResult = new SuiteResult(
      suiteName,
      testResults,
      totalDuration
    );

    // Generate HTML report
    try {
      HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
      reportGenerator.generateReport(suiteResult, deploymentName, baseUrl);
    } catch (Exception e) {
      logger.error(
        "Failed to generate HTML report for suite '{}'",
        suiteName,
        e
      );
    }

    return suiteResult;
  }

  @NotNull
  private static BaseTestSuite getBaseTestSuite(
    Class<? extends BaseTestSuite> suiteClass,
    String baseUrl,
    String deploymentName
  )
    throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    BaseTestSuite suiteInstance;
    try {
      Constructor<? extends BaseTestSuite> constructor = suiteClass.getConstructor(
        String.class,
        String.class
      );
      suiteInstance = constructor.newInstance(baseUrl, deploymentName);
    } catch (NoSuchMethodException e) {
      // Fall back to single-parameter constructor
      Constructor<? extends BaseTestSuite> constructor = suiteClass.getConstructor(
        String.class
      );
      suiteInstance = constructor.newInstance(baseUrl);
    }
    return suiteInstance;
  }
}
