package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TestExecutorFactory Integration Tests")
class TestExecutorFactoryIntegrationTest {

  private TestExecutorFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TestExecutorFactory();
  }

  @Test
  @DisplayName("Should create and execute SoundTransitTestSuite executor")
  void shouldCreateAndExecuteSoundTransitTestSuite() {
    // Given
    DeploymentConfiguration.DeploymentConfig deploymentConfig = new DeploymentConfiguration.DeploymentConfig();
    deploymentConfig.setName("SoundTransit");
    deploymentConfig.setOtpUrl("https://sound-transit-otp.ibi-transit.com");

    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite");
    testSuiteConfig.setSchedule("0 */10 * * * *");
    testSuiteConfig.setEnabled(true);

    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "sound-transit",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    assertNotNull(executor);
    assertEquals("sound-transit", executor.getDeploymentName());
    assertEquals("SoundTransitTestSuite", executor.getTestSuiteClass().getSimpleName());
    assertTrue(executor.isEnabled());
    assertTrue(executor.getTestMethodCount() > 0);
  }

  @Test
  @DisplayName("Should create and execute HopeLinkTestSuite executor")
  void shouldCreateAndExecuteHopeLinkTestSuite() {
    // Given
    DeploymentConfiguration.DeploymentConfig deploymentConfig = new DeploymentConfiguration.DeploymentConfig();
    deploymentConfig.setName("Hopelink");
    deploymentConfig.setOtpUrl("https://hopelink-otp.ibi-transit.com");

    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName("com.arcadis.otpsmoketests.tests.HopeLinkTestSuite");
    testSuiteConfig.setSchedule("0 */15 * * * *");
    testSuiteConfig.setEnabled(true);

    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "hopelink",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    assertNotNull(executor);
    assertEquals("hopelink", executor.getDeploymentName());
    assertEquals("HopeLinkTestSuite", executor.getTestSuiteClass().getSimpleName());
    assertTrue(executor.isEnabled());
    assertTrue(executor.getTestMethodCount() > 0);
  }

  @Test
  @DisplayName("Should create test suite instance successfully")
  void shouldCreateTestSuiteInstanceSuccessfully() {
    // Given
    Class<?> testSuiteClass = com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class;
    DeploymentContext deploymentContext = new DeploymentContext(
      "TestDeployment",
      "https://test-otp.example.com"
    );

    // When
    Object instance = factory.createTestSuiteInstance(testSuiteClass, deploymentContext);

    // Then
    assertNotNull(instance);
    assertInstanceOf(com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class, instance);
  }

  @Test
  @DisplayName("Should handle disabled test suite configuration")
  void shouldHandleDisabledTestSuiteConfiguration() {
    // Given
    DeploymentConfiguration.DeploymentConfig deploymentConfig = new DeploymentConfiguration.DeploymentConfig();
    deploymentConfig.setName("TestDeployment");
    deploymentConfig.setOtpUrl("https://test-otp.example.com");

    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite");
    testSuiteConfig.setSchedule("0 */10 * * * *");
    testSuiteConfig.setEnabled(false); // Disabled

    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "test-deployment",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    assertNotNull(executor);
    assertFalse(executor.isEnabled());
  }

  @Test
  @DisplayName("Should provide meaningful display names")
  void shouldProvideMeaningfulDisplayNames() {
    // Given
    DeploymentConfiguration.DeploymentConfig deploymentConfig = new DeploymentConfiguration.DeploymentConfig();
    deploymentConfig.setName("TestDeployment");
    deploymentConfig.setOtpUrl("https://test-otp.example.com");

    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite");
    testSuiteConfig.setSchedule("0 */10 * * * *");
    testSuiteConfig.setEnabled(true);

    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "test-deployment",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    String displayName = executor.getDisplayName();
    assertTrue(displayName.contains("SoundTransitTestSuite"));
    assertTrue(displayName.contains("TestDeployment"));
    assertTrue(displayName.contains("https://test-otp.example.com"));
  }
}