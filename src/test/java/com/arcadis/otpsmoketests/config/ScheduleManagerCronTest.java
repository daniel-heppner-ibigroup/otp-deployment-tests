package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

@ExtendWith(MockitoExtension.class)
class ScheduleManagerCronTest {

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
  void shouldValidateValidCronExpressions() {
    // Valid cron expressions
    assertTrue(scheduleManager.isValidScheduleExpression("0 */10 * * * *")); // Every 10 minutes
    assertTrue(scheduleManager.isValidScheduleExpression("0 0 */2 * * *"));  // Every 2 hours
    assertTrue(scheduleManager.isValidScheduleExpression("0 30 9 * * MON-FRI")); // 9:30 AM weekdays
    assertTrue(scheduleManager.isValidScheduleExpression("0 0 0 1 * *"));    // First day of month
  }

  @Test
  void shouldValidateValidSimpleIntervals() {
    // Valid simple intervals
    assertTrue(scheduleManager.isValidScheduleExpression("30s"));  // 30 seconds
    assertTrue(scheduleManager.isValidScheduleExpression("5m"));   // 5 minutes
    assertTrue(scheduleManager.isValidScheduleExpression("2h"));   // 2 hours
    assertTrue(scheduleManager.isValidScheduleExpression("120s")); // 120 seconds
    assertTrue(scheduleManager.isValidScheduleExpression("90m"));  // 90 minutes
  }

  @Test
  void shouldRejectInvalidScheduleExpressions() {
    // Invalid expressions
    assertFalse(scheduleManager.isValidScheduleExpression(""));
    assertFalse(scheduleManager.isValidScheduleExpression(null));
    assertFalse(scheduleManager.isValidScheduleExpression("invalid"));
    assertFalse(scheduleManager.isValidScheduleExpression("10"));     // Missing unit
    assertFalse(scheduleManager.isValidScheduleExpression("10x"));    // Invalid unit
    assertFalse(scheduleManager.isValidScheduleExpression("abc5m"));  // Invalid format
    assertFalse(scheduleManager.isValidScheduleExpression("0 */70 * * * *")); // Invalid cron (70 minutes)
  }

  @Test
  void shouldScheduleWithCronExpression() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "0 */15 * * * *", // Every 15 minutes
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
  void shouldScheduleWithSimpleInterval() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "5m", // Every 5 minutes
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
  void shouldFallbackToDefaultForInvalidSchedule() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "invalid-schedule",
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
  void shouldFallbackToDefaultForEmptySchedule() {
    // Given
    String deploymentName = "test-deployment";
    DeploymentConfiguration.DeploymentConfig deployment = createDeploymentConfig();
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = createTestSuiteConfig(
      "com.example.TestSuite",
      "",
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
  void shouldHandleVariousSimpleIntervalFormats() {
    // Test different valid simple interval formats
    String[] validIntervals = {"1s", "30s", "1m", "15m", "1h", "24h", "999s", "60m"};
    
    for (String interval : validIntervals) {
      assertTrue(
        scheduleManager.isValidScheduleExpression(interval),
        "Should validate interval: " + interval
      );
    }
  }

  @Test
  void shouldRejectInvalidSimpleIntervalFormats() {
    // Test invalid simple interval formats
    String[] invalidIntervals = {"0s", "-5m", "1.5m", "5ms", "1d", "1w", "s", "m", "h"};
    
    for (String interval : invalidIntervals) {
      assertFalse(
        scheduleManager.isValidScheduleExpression(interval),
        "Should reject interval: " + interval
      );
    }
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