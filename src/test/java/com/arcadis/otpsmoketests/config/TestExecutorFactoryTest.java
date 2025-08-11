package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TestExecutorFactory Tests")
class TestExecutorFactoryTest {

  private TestExecutorFactory factory;
  private DeploymentConfiguration.DeploymentConfig deploymentConfig;
  private DeploymentConfiguration.TestSuiteConfig testSuiteConfig;

  @BeforeEach
  void setUp() {
    factory = new TestExecutorFactory();

    // Set up test deployment configuration
    deploymentConfig = new DeploymentConfiguration.DeploymentConfig();
    deploymentConfig.setName("TestDeployment");
    deploymentConfig.setOtpUrl("https://test-otp.example.com");

    // Set up test suite configuration
    testSuiteConfig = new DeploymentConfiguration.TestSuiteConfig();
    testSuiteConfig.setClassName(
      "com.arcadis.otpsmoketests.tests.SoundTransitTestSuite"
    );
    testSuiteConfig.setSchedule("0 */10 * * * *");
    testSuiteConfig.setEnabled(true);
  }

  @Test
  @DisplayName("Should create executor for valid test suite")
  void shouldCreateExecutorForValidTestSuite() {
    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "test-deployment",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    assertNotNull(executor);
    assertEquals("test-deployment", executor.getDeploymentName());
    assertEquals(
      "com.arcadis.otpsmoketests.tests.SoundTransitTestSuite",
      executor.getTestSuiteClass().getName()
    );
    assertEquals("TestDeployment", executor.getDeploymentContext().getDeploymentName());
    assertEquals(
      "https://test-otp.example.com",
      executor.getDeploymentContext().getOtpUrl()
    );
    assertTrue(executor.isEnabled());
    assertEquals("0 */10 * * * *", executor.getSchedule());
  }

  @Test
  @DisplayName("Should throw exception for non-existent test suite class")
  void shouldThrowExceptionForNonExistentClass() {
    // Given
    testSuiteConfig.setClassName("com.example.NonExistentTestSuite");

    // When & Then
    assertThrows(
      TestExecutorFactory.TestSuiteInstantiationException.class,
      () -> factory.createExecutor("test-deployment", deploymentConfig, testSuiteConfig)
    );
  }

  @Test
  @DisplayName("Should throw exception for class that doesn't extend BaseTestSuite")
  void shouldThrowExceptionForInvalidBaseClass() {
    // Given - String class doesn't extend BaseTestSuite
    testSuiteConfig.setClassName("java.lang.String");

    // When & Then
    assertThrows(
      TestExecutorFactory.TestSuiteInstantiationException.class,
      () -> factory.createExecutor("test-deployment", deploymentConfig, testSuiteConfig)
    );
  }

  @Test
  @DisplayName("Should validate existing test suite classes")
  void shouldValidateExistingTestSuiteClasses() {
    // Test SoundTransitTestSuite
    assertTrue(
      factory.validateTestSuiteClass(
        "com.arcadis.otpsmoketests.tests.SoundTransitTestSuite"
      )
    );

    // Test HopeLinkTestSuite
    assertTrue(
      factory.validateTestSuiteClass(
        "com.arcadis.otpsmoketests.tests.HopeLinkTestSuite"
      )
    );
  }

  @Test
  @DisplayName("Should not validate non-existent classes")
  void shouldNotValidateNonExistentClasses() {
    assertFalse(factory.validateTestSuiteClass("com.example.NonExistentTestSuite"));
  }

  @Test
  @DisplayName("Should not validate classes that don't extend BaseTestSuite")
  void shouldNotValidateInvalidBaseClasses() {
    assertFalse(factory.validateTestSuiteClass("java.lang.String"));
  }

  @Test
  @DisplayName("Should create test suite instance with default constructor")
  void shouldCreateTestSuiteInstanceWithDefaultConstructor() {
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
    assertInstanceOf(
      com.arcadis.otpsmoketests.tests.SoundTransitTestSuite.class,
      instance
    );
  }

  @Test
  @DisplayName("Should handle deployment context creation correctly")
  void shouldHandleDeploymentContextCreation() {
    // When
    TestSuiteExecutor executor = factory.createExecutor(
      "test-deployment",
      deploymentConfig,
      testSuiteConfig
    );

    // Then
    DeploymentContext context = executor.getDeploymentContext();
    assertNotNull(context);
    assertEquals("TestDeployment", context.getDeploymentName());
    assertEquals("https://test-otp.example.com", context.getOtpUrl());
    assertEquals("TestDeployment (https://test-otp.example.com)", context.getDisplayName());
  }
}