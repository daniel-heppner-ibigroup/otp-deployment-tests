package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleConfigurationManagerTest {

  @Mock
  private DeploymentConfiguration deploymentConfiguration;

  @Mock
  private ScheduleManager scheduleManager;

  @Mock
  private ConfigurationValidator configurationValidator;

  private ScheduleConfigurationManager scheduleConfigurationManager;

  @BeforeEach
  void setUp() {
    scheduleConfigurationManager = new ScheduleConfigurationManager(
      deploymentConfiguration,
      scheduleManager,
      configurationValidator
    );
  }

  @Test
  void shouldValidateAndScheduleAllOnValidConfiguration() {
    // Given
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    deployment.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite1", "0 */5 * * * *", true),
      createTestSuiteConfig("com.example.TestSuite2", "0 */10 * * * *", true)
    ));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of("test-deployment", deployment));
    when(configurationValidator.validateConfiguration(deploymentConfiguration))
      .thenReturn(List.of()); // No validation errors

    // When
    scheduleConfigurationManager.validateAndScheduleAll();

    // Then
    verify(configurationValidator).validateConfiguration(deploymentConfiguration);
    verify(scheduleManager, times(2)).scheduleTestSuite(eq("test-deployment"), eq(deployment), any());
  }

  @Test
  void shouldSkipSchedulingOnValidationErrors() {
    // Given
    when(configurationValidator.validateConfiguration(deploymentConfiguration))
      .thenReturn(List.of("Invalid configuration", "Missing required field"));

    // When
    scheduleConfigurationManager.validateAndScheduleAll();

    // Then
    verify(configurationValidator).validateConfiguration(deploymentConfiguration);
    verify(scheduleManager, never()).scheduleTestSuite(any(), any(), any());
  }

  @Test
  void shouldSkipDisabledTestSuites() {
    // Given
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    deployment.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite1", "0 */5 * * * *", true),
      createTestSuiteConfig("com.example.TestSuite2", "0 */10 * * * *", false) // disabled
    ));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of("test-deployment", deployment));
    when(configurationValidator.validateConfiguration(deploymentConfiguration))
      .thenReturn(List.of());

    // When
    scheduleConfigurationManager.validateAndScheduleAll();

    // Then
    verify(scheduleManager, times(1)).scheduleTestSuite(eq("test-deployment"), eq(deployment), any());
  }

  @Test
  void shouldRefreshSchedules() {
    // When
    scheduleConfigurationManager.refreshSchedules();

    // Then
    verify(scheduleManager).rescheduleAll(deploymentConfiguration);
  }

  @Test
  void shouldScheduleSpecificTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    String testSuiteClassName = "com.example.TestSuite";
    
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      testSuiteClassName,
      "0 */5 * * * *",
      true
    );
    deployment.setTestSuites(List.of(testSuiteConfig));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of(deploymentName, deployment));

    // When
    boolean result = scheduleConfigurationManager.scheduleSpecificTestSuite(
      deploymentName,
      testSuiteClassName
    );

    // Then
    assertTrue(result);
    verify(scheduleManager).scheduleTestSuite(deploymentName, deployment, testSuiteConfig);
  }

  @Test
  void shouldReturnFalseForNonExistentDeployment() {
    // Given
    when(deploymentConfiguration.getDeployments()).thenReturn(Map.of());

    // When
    boolean result = scheduleConfigurationManager.scheduleSpecificTestSuite(
      "non-existent",
      "com.example.TestSuite"
    );

    // Then
    assertFalse(result);
    verify(scheduleManager, never()).scheduleTestSuite(any(), any(), any());
  }

  @Test
  void shouldReturnFalseForNonExistentTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    deployment.setTestSuites(List.of(
      createTestSuiteConfig("com.example.OtherTestSuite", "0 */5 * * * *", true)
    ));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of(deploymentName, deployment));

    // When
    boolean result = scheduleConfigurationManager.scheduleSpecificTestSuite(
      deploymentName,
      "com.example.NonExistentTestSuite"
    );

    // Then
    assertFalse(result);
    verify(scheduleManager, never()).scheduleTestSuite(any(), any(), any());
  }

  @Test
  void shouldReturnFalseForDisabledTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    String testSuiteClassName = "com.example.TestSuite";
    
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      testSuiteClassName,
      "0 */5 * * * *",
      false // disabled
    );
    deployment.setTestSuites(List.of(testSuiteConfig));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of(deploymentName, deployment));

    // When
    boolean result = scheduleConfigurationManager.scheduleSpecificTestSuite(
      deploymentName,
      testSuiteClassName
    );

    // Then
    assertFalse(result);
    verify(scheduleManager, never()).scheduleTestSuite(any(), any(), any());
  }

  @Test
  void shouldCancelSpecificTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    String testSuiteClassName = "com.example.TestSuite";
    String expectedTaskId = "test-deployment-TestSuite";

    when(scheduleManager.cancelScheduledTask(expectedTaskId)).thenReturn(true);

    // When
    boolean result = scheduleConfigurationManager.cancelSpecificTestSuite(
      deploymentName,
      testSuiteClassName
    );

    // Then
    assertTrue(result);
    verify(scheduleManager).cancelScheduledTask(expectedTaskId);
  }

  @Test
  void shouldReturnSchedulingStatus() {
    // Given
    DeploymentConfiguration.DeploymentConfig deployment1 = createDeploymentConfig();
    deployment1.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite1", "0 */5 * * * *", true),
      createTestSuiteConfig("com.example.TestSuite2", "0 */10 * * * *", false)
    ));

    DeploymentConfiguration.DeploymentConfig deployment2 = createDeploymentConfig();
    deployment2.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite3", "0 */15 * * * *", true)
    ));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of("deployment1", deployment1, "deployment2", deployment2));
    when(scheduleManager.getActiveTaskCount()).thenReturn(2);
    when(scheduleManager.getActiveSchedules())
      .thenReturn(Set.of("deployment1-TestSuite1", "deployment2-TestSuite3"));

    // When
    ScheduleConfigurationManager.SchedulingStatus status = 
      scheduleConfigurationManager.getSchedulingStatus();

    // Then
    assertEquals(2, status.getConfiguredDeployments());
    assertEquals(3, status.getConfiguredTestSuites());
    assertEquals(2, status.getEnabledTestSuites());
    assertEquals(2, status.getActiveSchedules());
    assertEquals(Set.of("deployment1-TestSuite1", "deployment2-TestSuite3"), status.getActiveTaskIds());
  }

  @Test
  void shouldHandleSchedulingExceptions() {
    // Given
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */5 * * * *",
      true
    );
    deployment.setTestSuites(List.of(testSuiteConfig));

    when(deploymentConfiguration.getDeployments())
      .thenReturn(Map.of("test-deployment", deployment));
    when(configurationValidator.validateConfiguration(deploymentConfiguration))
      .thenReturn(List.of());
    doThrow(new RuntimeException("Scheduling failed"))
      .when(scheduleManager).scheduleTestSuite(any(), any(), any());

    // When & Then - should not throw exception
    assertDoesNotThrow(() -> scheduleConfigurationManager.validateAndScheduleAll());
    
    verify(scheduleManager).scheduleTestSuite(eq("test-deployment"), eq(deployment), eq(testSuiteConfig));
  }

  private DeploymentConfiguration.DeploymentConfig createDeploymentConfig() {
    DeploymentConfiguration.DeploymentConfig config = new DeploymentConfiguration.DeploymentConfig();
    config.setName("Test Deployment");
    config.setOtpUrl("https://test.example.com");
    return config;
  }

  private DeploymentConfiguration.TestSuiteConfig createTestSuiteConfig(
    String className,
    String schedule,
    boolean enabled
  ) {
    DeploymentConfiguration.TestSuiteConfig config = new DeploymentConfiguration.TestSuiteConfig();
    config.setClassName(className);
    config.setSchedule(schedule);
    config.setEnabled(enabled);
    return config;
  }
}