package com.arcadis.otpsmoketests.runner;

import com.arcadis.otpsmoketests.BaseTestSuite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
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
      Throwable exception,
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
    String baseUrl
  ) {
    List<TestResult> testResults = new ArrayList<>();
    long suiteStartTime = System.nanoTime();

    try {
      // Create instance with baseUrl parameter
      Constructor<? extends BaseTestSuite> constructor = suiteClass.getConstructor(
        String.class
      );
      BaseTestSuite suiteInstance = constructor.newInstance(baseUrl);

      // Find all @Test methods
      Method[] methods = suiteClass.getMethods();
      for (Method method : methods) {
        if (method.isAnnotationPresent(Test.class)) {
          String testName = method.getName();
          long testStartTime = System.nanoTime();

          try {
            logger.debug("Running test: {}.{}", suiteName, testName);
            method.invoke(suiteInstance);
            long testDuration = (System.nanoTime() - testStartTime) / 1_000_000;
            testResults.add(new TestResult(testName, true, null, testDuration));
            logger.debug("Test passed: {}.{}", suiteName, testName);
          } catch (Exception e) {
            long testDuration = (System.nanoTime() - testStartTime) / 1_000_000;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            testResults.add(
              new TestResult(testName, false, cause, testDuration)
            );
            logger.error("Test failed: {}.{}", suiteName, testName, cause);
          }
        }
      }
    } catch (Exception e) {
      logger.error("Failed to instantiate test suite: {}", suiteName, e);
      testResults.add(new TestResult("suite_instantiation", false, e, 0));
    }

    long totalDuration = (System.nanoTime() - suiteStartTime) / 1_000_000;
    return new SuiteResult(suiteName, testResults, totalDuration);
  }
}
