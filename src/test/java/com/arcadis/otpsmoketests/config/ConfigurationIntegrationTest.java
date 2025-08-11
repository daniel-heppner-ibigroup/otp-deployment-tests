package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for deployment configuration loading and validation.
 * Tests the complete configuration-driven system startup and test suite scheduling.
 */
@SpringBootTest
@ActiveProfiles("deployments")
public class ConfigurationIntegrationTest {

  @Autowired
  private DeploymentConfiguration deploymentConfiguration;

  @Autowired
  private ScheduleManager scheduleManager;

  @Autowired
  private TestExecutorFactory testExecutorFactory;

  @Test
  public void testConfigurationLoading() {
    // Verify configuration is loaded
    assertNotNull(deploymentConfiguration, "DeploymentConfiguration should be loaded");
    assertNotNull(deploymentConfiguration.getDeployments(), "Deployments should be loaded");
    assertFalse(deploymentConfiguration.getDeployments().isEmpty(), "Should have at least one deployment");

    // Verify geocoding configuration
    assertNotNull(deploymentConfiguration.getGeocoding(), "Geocoding configuration should be loaded");
    assertNotNull(deploymentConfiguration.getGeocoding().getPeliasBaseUrl(), "Pelias URL should be configured");
    assertTrue(deploymentConfiguration.getGeocoding().getFocusLatitude() != 0, "Focus latitude should be set");
    assertTrue(deploymentConfiguration.getGeocoding().getFocusLongitude() != 0, "Focus longitude should be set");
  }

  @Test
  public void testDeploymentConfigurations() {
    var deployments = deploymentConfiguration.getDeployments();

    // Verify SoundTransit deployment
    assertTrue(deployments.containsKey("sound-transit"), "Should have sound-transit deployment");
    var soundTransit = deployments.get("sound-transit");
    assertEquals("SoundTransit", soundTransit.getName());
    assertEquals("https://sound-transit-otp.ibi-transit.com", soundTransit.getOtpUrl());
    assertFalse(soundTransit.getTestSuites().isEmpty(), "SoundTransit should have test suites");

    // Verify HopeLink deployment
    assertTrue(deployments.containsKey("hopelink"), "Should have hopelink deployment");
    var hopelink = deployments.get("hopelink");
    assertEquals("Hopelink", hopelink.getName());
    assertEquals("https://hopelink-otp.ibi-transit.com", hopelink.getOtpUrl());
    assertFalse(hopelink.getTestSuites().isEmpty(), "HopeLink should have test suites");
  }

  @Test
  public void testTestSuiteConfigurations() {
    var deployments = deploymentConfiguration.getDeployments();

    // Check SoundTransit test suites
    var soundTransit = deployments.get("sound-transit");
    var soundTransitSuites = soundTransit.getTestSuites();
    assertEquals(1, soundTransitSuites.size(), "SoundTransit should have 1 test suite");
    
    var soundTransitSuite = soundTransitSuites.get(0);
    assertEquals("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite", soundTransitSuite.getClassName());
    assertEquals("0 */10 * * * *", soundTransitSuite.getSchedule());
    assertTrue(soundTransitSuite.isEnabled());

    // Check HopeLink test suites
    var hopelink = deployments.get("hopelink");
    var hopelinkSuites = hopelink.getTestSuites();
    assertEquals(2, hopelinkSuites.size(), "HopeLink should have 2 test suites");
    
    // Verify HopeLink test suite
    var hopelinkSuite = hopelinkSuites.stream()
      .filter(suite -> suite.getClassName().equals("com.arcadis.otpsmoketests.tests.HopeLinkTestSuite"))
      .findFirst()
      .orElse(null);
    assertNotNull(hopelinkSuite, "Should have HopeLinkTestSuite");
    assertEquals("0 */15 * * * *", hopelinkSuite.getSchedule());
    assertTrue(hopelinkSuite.isEnabled());

    // Verify SoundTransit test suite on HopeLink deployment
    var soundTransitOnHopelink = hopelinkSuites.stream()
      .filter(suite -> suite.getClassName().equals("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite"))
      .findFirst()
      .orElse(null);
    assertNotNull(soundTransitOnHopelink, "Should have SoundTransitTestSuite on HopeLink");
    assertEquals("0 0 */2 * * *", soundTransitOnHopelink.getSchedule());
    assertTrue(soundTransitOnHopelink.isEnabled());
  }

  @Test
  public void testTestSuiteInstantiation() {
    var deployments = deploymentConfiguration.getDeployments();
    var geocodingConfig = deploymentConfiguration.getGeocoding();

    // Test creating executors for all configured test suites
    for (var deploymentEntry : deployments.entrySet()) {
      String deploymentName = deploymentEntry.getKey();
      var deployment = deploymentEntry.getValue();

      for (var testSuiteConfig : deployment.getTestSuites()) {
        if (!testSuiteConfig.isEnabled()) {
          continue;
        }

        // Verify that test executor can be created
        assertDoesNotThrow(() -> {
          TestSuiteExecutor executor = testExecutorFactory.createExecutor(
            deploymentName, deployment, testSuiteConfig, geocodingConfig
          );
          assertNotNull(executor, "Test executor should be created");
          assertTrue(executor.isValid(), "Test executor should be valid");
        }, String.format("Should be able to create executor for %s on %s", 
                        testSuiteConfig.getClassName(), deploymentName));
      }
    }
  }

  @Test
  public void testScheduleManagerIntegration() {
    // Verify that ScheduleManager can schedule all test suites
    assertDoesNotThrow(() -> {
      scheduleManager.rescheduleAll(deploymentConfiguration);
    }, "ScheduleManager should be able to schedule all test suites");

    // Verify that schedules are active
    var activeSchedules = scheduleManager.getActiveSchedules();
    assertFalse(activeSchedules.isEmpty(), "Should have active schedules");
    
    // We expect 3 total schedules: 1 for sound-transit, 2 for hopelink
    assertEquals(3, activeSchedules.size(), "Should have 3 active schedules");
    
    // Verify schedule names
    assertTrue(activeSchedules.contains("sound-transit-SoundTransitTestSuite"), 
              "Should have SoundTransit schedule");
    assertTrue(activeSchedules.contains("hopelink-HopeLinkTestSuite"), 
              "Should have HopeLink schedule");
    assertTrue(activeSchedules.contains("hopelink-SoundTransitTestSuite"), 
              "Should have SoundTransit on HopeLink schedule");
  }

  @Test
  public void testConfigurationValidation() {
    // Test that all configured test suite classes exist and are valid
    for (var deploymentEntry : deploymentConfiguration.getDeployments().entrySet()) {
      var deployment = deploymentEntry.getValue();
      
      for (var testSuiteConfig : deployment.getTestSuites()) {
        assertTrue(
          testExecutorFactory.validateTestSuiteClass(testSuiteConfig.getClassName()),
          String.format("Test suite class should be valid: %s", testSuiteConfig.getClassName())
        );
      }
    }

    // Test schedule expression validation
    for (var deploymentEntry : deploymentConfiguration.getDeployments().entrySet()) {
      var deployment = deploymentEntry.getValue();
      
      for (var testSuiteConfig : deployment.getTestSuites()) {
        assertTrue(
          scheduleManager.isValidScheduleExpression(testSuiteConfig.getSchedule()),
          String.format("Schedule expression should be valid: %s", testSuiteConfig.getSchedule())
        );
      }
    }
  }
}