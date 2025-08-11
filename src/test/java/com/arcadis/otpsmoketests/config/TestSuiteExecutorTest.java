package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TestSuiteExecutor Tests")
class TestSuiteExecutorTest {

  private TestSuiteExecutor executor;
  private DeploymentContext deploymentContext;
  private DeploymentConfiguration.TestSuiteConfig testSuiteConfig;

  @BeforeEach
  void setUp() {
    deploymentContext = new DeploymentContext(
      "TestDeployment",
      "https://test-otp.example.com"
    );

    testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite");
    testSuiteConfig.setSchedule("0 */10 * * * *");
    testSuiteConfig.setEnabled(true);

    executor = new TestSuiteExecutor(
      "test-deployment",
      com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class,
      deploymentContext,
      testSuiteConfig
    );
  }

  @Test
  @DisplayName("Should create executor with correct properties")
  void shouldCreateExecutorWithCorrectProperties() {
    assertEquals("test-deployment", executor.getDeploymentName());
    assertEquals(
      com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class,
      executor.getTestSuiteClass()
    );
    assertEquals(deploymentContext, executor.getDeploymentContext());
    assertEquals(testSuiteConfig, executor.getTestSuiteConfig());
    assertTrue(executor.isEnabled());
    assertEquals("0 */10 * * * *", executor.getSchedule());
  }

  @Test
  @DisplayName("Should count test methods correctly")
  void shouldCountTestMethodsCorrectly() {
    int testMethodCount = executor.getTestMethodCount();
    assertTrue(testMethodCount > 0, "Should find test methods in SoundTransitTestSuite");
  }

  @Test
  @DisplayName("Should provide meaningful display name")
  void shouldProvideMeaningfulDisplayName() {
    String displayName = executor.getDisplayName();
    assertTrue(displayName.contains("SoundTransitTestSuite"));
    assertTrue(displayName.contains("TestDeployment"));
    assertTrue(displayName.contains("https://test-otp.example.com"));
  }

  @Test
  @DisplayName("Should validate executor correctly")
  void shouldValidateExecutorCorrectly() {
    assertTrue(executor.isValid(), "Executor should be valid for SoundTransitTestSuite");
  }

  @Test
  @DisplayName("Should perform dry run successfully")
  void shouldPerformDryRunSuccessfully() {
    TestExecutionResult result = executor.dryRun();
    
    assertNotNull(result);
    assertEquals("test-deployment", result.getDeploymentName());
    assertEquals("SoundTransitTestSuite", result.getTestSuiteName());
    assertTrue(result.isSuccess());
    assertTrue(result.getTestsRun() > 0);
    assertTrue(result.getExecutionTimeMs() >= 0);
  }

  @Test
  @DisplayName("Should handle disabled test suite")
  void shouldHandleDisabledTestSuite() {
    testSuiteConfig.setEnabled(false);
    
    TestSuiteExecutor disabledExecutor = new TestSuiteExecutor(
      "test-deployment",
      com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class,
      deploymentContext,
      testSuiteConfig
    );

    assertFalse(disabledExecutor.isEnabled());
  }

  @Test
  @DisplayName("Should provide meaningful toString")
  void shouldProvideMeaningfulToString() {
    String toString = executor.toString();
    assertTrue(toString.contains("test-deployment"));
    assertTrue(toString.contains("SoundTransitTestSuite"));
    assertTrue(toString.contains("enabled=true"));
    assertTrue(toString.contains("schedule=0 */10 * * * *"));
  }
}