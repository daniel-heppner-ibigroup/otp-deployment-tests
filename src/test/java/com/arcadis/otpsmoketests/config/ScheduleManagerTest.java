package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

@ExtendWith(MockitoExtension.class)
class ScheduleManagerTest {

  @Mock
  private TaskScheduler taskScheduler;

  @Mock
  private TestExecutorFactory testExecutorFactory;

  @Mock
  private TestSuiteExecutor testSuiteExecutor;

  @Mock
  private ScheduledFuture<?> scheduledFuture;

  private ScheduleManager scheduleManager;

  @BeforeEach
  void setUp() {
    scheduleManager = new ScheduleManager(taskScheduler, testExecutorFactory);
  }

  @Test
  void shouldScheduleEnabledTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */5 * * * *",
      true
    );

    when(testExecutorFactory.createExecutor(deploymentName, deployment, testSuiteConfig))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);

    // When
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig);

    // Then
    verify(testExecutorFactory).createExecutor(deploymentName, deployment, testSuiteConfig);
    verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    assertEquals(1, scheduleManager.getActiveTaskCount());
    assertTrue(scheduleManager.getActiveSchedules().contains("test-deployment-TestSuite"));
  }

  @Test
  void shouldSkipDisabledTestSuite() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */5 * * * *",
      false
    );

    // When
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig);

    // Then
    verify(testExecutorFactory, never()).createExecutor(any(), any(), any());
    verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    assertEquals(0, scheduleManager.getActiveTaskCount());
  }

  @Test
  void shouldUseDefaultScheduleForInvalidCronExpression() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "invalid-cron",
      true
    );

    when(testExecutorFactory.createExecutor(deploymentName, deployment, testSuiteConfig))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);

    // When
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig);

    // Then
    verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    assertEquals(1, scheduleManager.getActiveTaskCount());
  }

  @Test
  void shouldRescheduleAllTestSuites() {
    // Given
    DeploymentConfiguration config = new DeploymentConfiguration();
    
    DeploymentConfiguration.DeploymentConfig deployment1 = createDeploymentConfig();
    deployment1.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite1", "0 */5 * * * *", true)
    ));
    
    DeploymentConfiguration.DeploymentConfig deployment2 = createDeploymentConfig();
    deployment2.setTestSuites(List.of(
      createTestSuiteConfig("com.example.TestSuite2", "0 */10 * * * *", true)
    ));
    
    config.setDeployments(Map.of(
      "deployment1", deployment1,
      "deployment2", deployment2
    ));

    when(testExecutorFactory.createExecutor(any(), any(), any()))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);

    // When
    scheduleManager.rescheduleAll(config);

    // Then
    verify(testExecutorFactory, times(2)).createExecutor(any(), any(), any());
    verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    assertEquals(2, scheduleManager.getActiveTaskCount());
  }

  @Test
  void shouldCancelScheduledTask() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */5 * * * *",
      true
    );

    when(testExecutorFactory.createExecutor(deploymentName, deployment, testSuiteConfig))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);
    when(scheduledFuture.cancel(false)).thenReturn(true);

    // Schedule first
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig);
    assertEquals(1, scheduleManager.getActiveTaskCount());

    // When
    boolean canceled = scheduleManager.cancelScheduledTask("test-deployment-TestSuite");

    // Then
    assertTrue(canceled);
    verify(scheduledFuture).cancel(false);
    assertEquals(0, scheduleManager.getActiveTaskCount());
  }

  @Test
  void shouldReturnFalseWhenCancelingNonExistentTask() {
    // When
    boolean canceled = scheduleManager.cancelScheduledTask("non-existent-task");

    // Then
    assertFalse(canceled);
  }

  @Test
  void shouldCancelAllTasks() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig1 = createTestSuiteConfig(
      "com.example.TestSuite1",
      "0 */5 * * * *",
      true
    );
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig2 = createTestSuiteConfig(
      "com.example.TestSuite2",
      "0 */10 * * * *",
      true
    );

    when(testExecutorFactory.createExecutor(any(), any(), any()))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);

    // Schedule multiple tasks
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig1);
    scheduleManager.scheduleTestSuite(deploymentName, deployment, testSuiteConfig2);
    assertEquals(2, scheduleManager.getActiveTaskCount());

    // When
    scheduleManager.cancelAllTasks();

    // Then
    verify(scheduledFuture, times(2)).cancel(false);
    assertEquals(0, scheduleManager.getActiveTaskCount());
    assertTrue(scheduleManager.getActiveSchedules().isEmpty());
  }

  @Test
  void shouldGenerateUniqueTaskIds() {
    // Given
    String deploymentName1 = "deployment1";
    String deploymentName2 = "deployment2";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */5 * * * *",
      true
    );

    when(testExecutorFactory.createExecutor(any(), any(), any()))
      .thenReturn(testSuiteExecutor);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
      .thenReturn(scheduledFuture);

    // When
    scheduleManager.scheduleTestSuite(deploymentName1, deployment, testSuiteConfig);
    scheduleManager.scheduleTestSuite(deploymentName2, deployment, testSuiteConfig);

    // Then
    assertEquals(2, scheduleManager.getActiveTaskCount());
    assertTrue(scheduleManager.getActiveSchedules().contains("deployment1-TestSuite"));
    assertTrue(scheduleManager.getActiveSchedules().contains("deployment2-TestSuite"));
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