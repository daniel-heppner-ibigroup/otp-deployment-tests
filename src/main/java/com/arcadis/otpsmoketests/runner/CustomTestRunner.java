package com.arcadis.otpsmoketests.runner;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.reporting.TripCapture;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
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
    private final String displayName;
    private final List<TripCapture.CapturedRequest> requests;

    public TestResult(
      String testName,
      boolean passed,
      Throwable exception,
      long durationMs
    ) {
      this(testName, testName, passed, exception, durationMs, List.of());
    }

    public TestResult(
      String testName,
      String displayName,
      boolean passed,
      Throwable exception,
      long durationMs,
      List<TripCapture.CapturedRequest> requests
    ) {
      this.testName = testName;
      this.displayName = displayName;
      this.passed = passed;
      this.exception = exception;
      this.durationMs = durationMs;
      this.requests = List.copyOf(requests);
    }

    public String getDisplayName() {
      return displayName;
    }

    public List<TripCapture.CapturedRequest> getRequests() {
      return requests;
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
    long totalDurationMs,
    Instant startedAt
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
    Instant startedAt = Instant.now();
    long suiteStartTime = System.nanoTime();

    BaseTestSuite suiteInstance;
    try (TripCapture setupCapture = TripCapture.begin()) {
      try {
        suiteInstance = getBaseTestSuite(suiteClass, baseUrl, deploymentName);
      } catch (
        ReflectiveOperationException | RuntimeException | AssertionError e
      ) {
        Throwable cause = unwrap(e);
        testResults.add(
          new TestResult(
            "suite-setup",
            "Suite setup",
            false,
            cause,
            elapsed(suiteStartTime),
            setupCapture.requests()
          )
        );
        logger.error("Failed to initialize test suite: {}", suiteName, cause);
        return new SuiteResult(
          suiteName,
          testResults,
          elapsed(suiteStartTime),
          startedAt
        );
      }
    }
    var methods = Arrays
      .stream(suiteClass.getMethods())
      .filter(method -> method.isAnnotationPresent(Test.class))
      .sorted(Comparator.comparing(Method::getName))
      .toList();
    for (Method method : methods) {
      long start = System.nanoTime();
      String name = method.getName();
      String displayName = method.isAnnotationPresent(DisplayName.class)
        ? method.getAnnotation(DisplayName.class).value()
        : name;
      try (TripCapture capture = TripCapture.begin()) {
        Throwable failure = null;
        try {
          method.invoke(suiteInstance);
        } catch (
          ReflectiveOperationException | RuntimeException | AssertionError e
        ) {
          failure = unwrap(e);
          if (failure instanceof VirtualMachineError fatal) throw fatal;
          if (failure instanceof ThreadDeath fatal) throw fatal;
          logger.error("Test failed: {}.{}", suiteName, name, failure);
        }
        testResults.add(
          new TestResult(
            name,
            displayName,
            failure == null,
            failure,
            elapsed(start),
            capture.requests()
          )
        );
      }
    }
    return new SuiteResult(
      suiteName,
      testResults,
      elapsed(suiteStartTime),
      startedAt
    );
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private static Throwable unwrap(Throwable failure) {
    return failure instanceof InvocationTargetException invocation
      ? invocation.getTargetException()
      : failure;
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
