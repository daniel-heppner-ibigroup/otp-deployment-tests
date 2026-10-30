package com.arcadis.otpsmoketests.runner;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.ExecutableTestCase;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

  public record TestResult(
    String testName,
    boolean passed,
    Throwable exception,
    long durationMs
  ) {}

  public record SuiteResult(
    String suiteName,
    List<TestResult> testResults,
    long totalDurationMs
  ) {
    public long getTestsFoundCount() {
      return testResults.size();
    }

    public long getTestsSucceededCount() {
      return testResults.stream().mapToLong(t -> t.passed() ? 1 : 0).sum();
    }

    public long getTestsFailedCount() {
      return testResults.stream().mapToLong(t -> t.passed() ? 0 : 1).sum();
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

    List<ExecutableTestCase> testCases;
    try {
      BaseTestSuite suiteInstance = getBaseTestSuite(
        suiteClass,
        baseUrl,
        deploymentName
      );
      testCases = discoverTestCases(suiteClass, suiteInstance);
    } catch (
      ReflectiveOperationException | RuntimeException | AssertionError e
    ) {
      Throwable cause = unwrap(e);
      testResults.add(
        new TestResult("suite-setup", false, cause, elapsed(suiteStartTime))
      );
      logger.error("Failed to initialize test suite: {}", suiteName, cause);
      return new SuiteResult(suiteName, testResults, elapsed(suiteStartTime));
    }
    for (ExecutableTestCase testCase : testCases) {
      long start = System.nanoTime();
      Throwable failure = null;
      try {
        testCase.execute();
      } catch (Throwable e) {
        failure = unwrap(e);
        if (failure instanceof VirtualMachineError fatal) throw fatal;
        logger.error("Test failed: {}.{}", suiteName, testCase.id(), failure);
      }
      testResults.add(
        new TestResult(testCase.id(), failure == null, failure, elapsed(start))
      );
    }
    return new SuiteResult(suiteName, testResults, elapsed(suiteStartTime));
  }

  private static List<ExecutableTestCase> discoverTestCases(
    Class<? extends BaseTestSuite> suiteClass,
    BaseTestSuite suiteInstance
  ) {
    List<ExecutableTestCase> testCases = new ArrayList<>();

    Arrays
      .stream(suiteClass.getMethods())
      .filter(method -> method.isAnnotationPresent(Test.class))
      .sorted(Comparator.comparing(Method::getName))
      .map(method -> executableMethod(suiteInstance, method))
      .forEach(testCases::add);

    testCases.addAll(suiteInstance.testCases());

    Set<String> ids = new HashSet<>();
    for (ExecutableTestCase testCase : testCases) {
      if (!ids.add(testCase.id())) {
        throw new IllegalArgumentException(
          "Duplicate test case ID: " + testCase.id()
        );
      }
    }
    return List.copyOf(testCases);
  }

  private static ExecutableTestCase executableMethod(
    BaseTestSuite suiteInstance,
    Method method
  ) {
    String name = method.getName();
    String displayName = method.isAnnotationPresent(DisplayName.class)
      ? method.getAnnotation(DisplayName.class).value()
      : name;
    return new ExecutableTestCase(
      name,
      displayName,
      () -> method.invoke(suiteInstance)
    );
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }

  private static Throwable unwrap(Throwable failure) {
    Throwable current = failure;
    while (current instanceof InvocationTargetException invocation) {
      current = invocation.getTargetException();
    }
    return current;
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
