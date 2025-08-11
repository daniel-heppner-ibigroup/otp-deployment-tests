package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end validation test for the complete configurable deployment testing system.
 * This test validates the entire system behavior including configuration loading,
 * test suite scheduling, execution, and metrics collection.
 */
@SpringBootTest
@ActiveProfiles("deployments")
public class EndToEndValidationTest {

  @Autowired
  private DeploymentConfiguration deploymentConfiguration;

  @Autowired
  private ScheduleManager scheduleManager;

  @Autowired
  private TestExecutorFactory testExecutorFactory;

  @Test
  public void testCompleteSystemInitialization() {
    // Verify that all components are properly initialized
    assertNotNull(deploymentConfiguration, "DeploymentConfiguration should be initialized");
    assertNotNull(scheduleManager, "ScheduleManager should be initialized");
    assertNotNull(testExecutorFactory, "TestExecutorFactory should be initialized");

    // Verify configuration is loaded correctly
    assertFalse(deploymentConfiguration.getDeployments().isEmpty(), 
               "Should have deployments configured");
    assertNotNull(deploymentConfiguration.getGeocoding(), 
                 "Should have geocoding configuration");

    // Verify that all configured test suites can be validated
    for (var deploymentEntry : deploymentConfiguration.getDeployments().entrySet()) {
      var deployment = deploymentEntry.getValue();
      
      for (var testSuiteConfig : deployment.getTestSuites()) {
        assertTrue(
          testExecutorFactory.validateTestSuiteClass(testSuiteConfig.getClassName()),
          String.format("Test suite should be valid: %s", testSuiteConfig.getClassName())
        );
      }
    }
  }

  @Test
  public void testSchedulingSystemStability() {
    // Test that the scheduling system can handle multiple reschedule operations
    int initialActiveCount = scheduleManager.getActiveTaskCount();
    
    // Reschedule multiple times to test stability
    for (int i = 0; i < 3; i++) {
      assertDoesNotThrow(() -> {
        scheduleManager.rescheduleAll(deploymentConfiguration);
      }, "Rescheduling should not throw exceptions");
      
      // Verify that we have the expected number of active schedules
      assertEquals(3, scheduleManager.getActiveTaskCount(), 
                  "Should maintain correct number of active schedules");
    }
  }

  @Test
  public void testTestSuiteExecutionStability() {
    var deployments = deploymentConfiguration.getDeployments();
    var geocodingConfig = deploymentConfiguration.getGeocoding();

    // Test that all test suite executors can be created and validated
    for (var deploymentEntry : deployments.entrySet()) {
      String deploymentName = deploymentEntry.getKey();
      var deployment = deploymentEntry.getValue();

      for (var testSuiteConfig : deployment.getTestSuites()) {
        if (!testSuiteConfig.isEnabled()) {
          continue;
        }

        // Create executor
        TestSuiteExecutor executor = testExecutorFactory.createExecutor(
          deploymentName, deployment, testSuiteConfig, geocodingConfig
        );

        // Validate executor
        assertTrue(executor.isValid(), 
                  String.format("Executor should be valid for %s on %s", 
                               testSuiteConfig.getClassName(), deploymentName));

        // Test dry run
        assertDoesNotThrow(() -> {
          TestExecutionResult dryRunResult = executor.dryRun();
          assertNotNull(dryRunResult, "Dry run should return result");
          assertEquals(deploymentName, dryRunResult.getDeploymentName(), 
                      "Dry run result should have correct deployment name");
        }, String.format("Dry run should work for %s on %s", 
                        testSuiteConfig.getClassName(), deploymentName));
      }
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  public void testConfigurationConsistency() {
    // Verify that the configuration matches expected values from the YAML file
    var deployments = deploymentConfiguration.getDeployments();
    
    // Verify SoundTransit deployment
    assertTrue(deployments.containsKey("sound-transit"), "Should have sound-transit");
    var soundTransit = deployments.get("sound-transit");
    assertEquals("SoundTransit", soundTransit.getName());
    assertEquals("https://sound-transit-otp.ibi-transit.com", soundTransit.getOtpUrl());
    assertEquals(1, soundTransit.getTestSuites().size());
    
    var soundTransitSuite = soundTransit.getTestSuites().get(0);
    assertEquals("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite", 
                soundTransitSuite.getClassName());
    assertEquals("0 */10 * * * *", soundTransitSuite.getSchedule());
    assertTrue(soundTransitSuite.isEnabled());

    // Verify HopeLink deployment
    assertTrue(deployments.containsKey("hopelink"), "Should have hopelink");
    var hopelink = deployments.get("hopelink");
    assertEquals("Hopelink", hopelink.getName());
    assertEquals("https://hopelink-otp.ibi-transit.com", hopelink.getOtpUrl());
    assertEquals(2, hopelink.getTestSuites().size());

    // Verify geocoding configuration
    var geocoding = deploymentConfiguration.getGeocoding();
    assertEquals("https://api.geocode.earth/v1", geocoding.getPeliasBaseUrl());
    assertEquals(47.6062, geocoding.getFocusLatitude(), 0.0001);
    assertEquals(-122.3321, geocoding.getFocusLongitude(), 0.0001);
  }

  @Test
  public void testScheduleExpressionValidation() {
    // Test that all configured schedule expressions are valid
    for (var deploymentEntry : deploymentConfiguration.getDeployments().entrySet()) {
      var deployment = deploymentEntry.getValue();
      
      for (var testSuiteConfig : deployment.getTestSuites()) {
        assertTrue(
          scheduleManager.isValidScheduleExpression(testSuiteConfig.getSchedule()),
          String.format("Schedule expression should be valid: %s", testSuiteConfig.getSchedule())
        );
      }
    }

    // Test some additional schedule expressions
    assertTrue(scheduleManager.isValidScheduleExpression("0 */5 * * * *"), 
              "Every 5 minutes should be valid");
    assertTrue(scheduleManager.isValidScheduleExpression("0 0 */1 * * *"), 
              "Every hour should be valid");
    assertTrue(scheduleManager.isValidScheduleExpression("10m"), 
              "Simple interval should be valid");
    
    assertFalse(scheduleManager.isValidScheduleExpression("invalid"), 
               "Invalid expression should be rejected");
    assertFalse(scheduleManager.isValidScheduleExpression(""), 
               "Empty expression should be rejected");
  }

  @Test
  public void testDeploymentContextCreation() {
    // Test that deployment contexts are created correctly
    for (var deploymentEntry : deploymentConfiguration.getDeployments().entrySet()) {
      String deploymentName = deploymentEntry.getKey();
      var deployment = deploymentEntry.getValue();

      DeploymentContext context = new DeploymentContext(
        deployment.getName(), 
        deployment.getOtpUrl()
      );

      assertEquals(deployment.getName(), context.getDeploymentName());
      assertEquals(deployment.getOtpUrl(), context.getOtpUrl());
      assertNotNull(context.getDisplayName());
      assertTrue(context.getDisplayName().contains(deployment.getName()));
    }
  }

  @Test
  public void testSystemResourceUsage() {
    // Test that the system doesn't create excessive resources
    int initialActiveSchedules = scheduleManager.getActiveTaskCount();
    
    // Reschedule and verify resource cleanup
    scheduleManager.rescheduleAll(deploymentConfiguration);
    
    assertEquals(3, scheduleManager.getActiveTaskCount(), 
                "Should have exactly 3 active schedules");
    
    // Test canceling all tasks
    scheduleManager.cancelAllTasks();
    assertEquals(0, scheduleManager.getActiveTaskCount(), 
                "Should have no active schedules after canceling all");
    
    // Reschedule again to restore normal state
    scheduleManager.rescheduleAll(deploymentConfiguration);
    assertEquals(3, scheduleManager.getActiveTaskCount(), 
                "Should restore 3 active schedules");
  }

  @Test
  public void testErrorHandlingResilience() {
    // Test that the system handles various error conditions gracefully
    
    // Test with invalid deployment configuration
    DeploymentConfiguration.DeploymentConfig invalidDeployment = 
      new DeploymentConfiguration.DeploymentConfig();
    invalidDeployment.setName("invalid");
    invalidDeployment.setOtpUrl("invalid-url");
    
    DeploymentConfiguration.TestSuiteConfig invalidTestSuite = 
      new DeploymentConfiguration.TestSuiteConfig();
    invalidTestSuite.setClassName("com.invalid.NonExistentTestSuite");
    invalidTestSuite.setSchedule("0 */10 * * * *");
    invalidTestSuite.setEnabled(true);
    
    // Test that invalid test suite class is properly rejected
    assertFalse(
      testExecutorFactory.validateTestSuiteClass(invalidTestSuite.getClassName()),
      "Invalid test suite class should be rejected"
    );
    
    // Test that invalid schedule expressions are handled
    assertFalse(
      scheduleManager.isValidScheduleExpression("invalid-cron"),
      "Invalid schedule expression should be rejected"
    );
  }

  @Test
  public void testConfigurationHotReloadingCapability() {
    // Test the system's capability to handle configuration changes
    // Note: This tests the infrastructure, not actual file watching
    
    int initialScheduleCount = scheduleManager.getActiveTaskCount();
    
    // Cancel all schedules
    scheduleManager.cancelAllTasks();
    assertEquals(0, scheduleManager.getActiveTaskCount(), 
                "Should have no schedules after canceling");
    
    // Reschedule with current configuration (simulating hot reload)
    scheduleManager.rescheduleAll(deploymentConfiguration);
    assertEquals(initialScheduleCount, scheduleManager.getActiveTaskCount(), 
                "Should restore original schedule count after hot reload");
    
    // Verify that all expected schedules are active
    var activeSchedules = scheduleManager.getActiveSchedules();
    assertTrue(activeSchedules.contains("sound-transit-SoundTransitTestSuite"), 
              "Should have SoundTransit schedule");
    assertTrue(activeSchedules.contains("hopelink-HopeLinkTestSuite"), 
              "Should have HopeLink schedule");
    assertTrue(activeSchedules.contains("hopelink-SoundTransitTestSuite"), 
              "Should have SoundTransit on HopeLink schedule");
  }
}