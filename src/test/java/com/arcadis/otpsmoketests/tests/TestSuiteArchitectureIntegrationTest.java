package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.config.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify the updated test suite architecture works correctly
 * with deployment context constructors.
 */
public class TestSuiteArchitectureIntegrationTest {

  @Test
  public void testSoundTransitTestSuiteWithDeploymentContext() {
    var deploymentContext = new DeploymentContext(
      "TestSoundTransit",
      "https://test-sound-transit-otp.example.com"
    );
    
    var testSuite = new SoundTransitTestSuite(
      deploymentContext,
      "https://87sp37ezga.execute-api.us-east-1.amazonaws.com/st/autocomplete",
      47.61097,
      -122.33701
    );
    
    assertNotNull(testSuite);
    // Verify that the deployment context values are used
    assertEquals("TestSoundTransit", testSuite.suiteName);
    assertEquals("https://test-sound-transit-otp.example.com", testSuite.otpWebUrl);
  }

  @Test
  public void testHopeLinkTestSuiteWithDeploymentContext() {
    var deploymentContext = new DeploymentContext(
      "TestHopelink",
      "https://test-hopelink-otp.example.com"
    );
    
    var testSuite = new HopeLinkTestSuite(
      deploymentContext,
      "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
      47.61097,
      -122.33701
    );
    
    assertNotNull(testSuite);
    // Verify that the deployment context values are used
    assertEquals("TestHopelink", testSuite.suiteName);
    assertEquals("https://test-hopelink-otp.example.com", testSuite.otpWebUrl);
  }

  @Test
  public void testFactoryMethodWithSoundTransit() {
    var deploymentContext = new DeploymentContext(
      "FactorySoundTransit",
      "https://factory-sound-transit-otp.example.com"
    );
    
    var testSuite = BaseTestSuite.createWithDeploymentContext(
      SoundTransitTestSuite.class,
      deploymentContext,
      "https://87sp37ezga.execute-api.us-east-1.amazonaws.com/st/autocomplete",
      47.61097,
      -122.33701
    );
    
    assertNotNull(testSuite);
    assertInstanceOf(SoundTransitTestSuite.class, testSuite);
    assertEquals("FactorySoundTransit", testSuite.suiteName);
    assertEquals("https://factory-sound-transit-otp.example.com", testSuite.otpWebUrl);
  }

  @Test
  public void testFactoryMethodWithHopeLink() {
    var deploymentContext = new DeploymentContext(
      "FactoryHopelink",
      "https://factory-hopelink-otp.example.com"
    );
    
    var testSuite = BaseTestSuite.createWithDeploymentContext(
      HopeLinkTestSuite.class,
      deploymentContext,
      "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
      47.61097,
      -122.33701
    );
    
    assertNotNull(testSuite);
    assertInstanceOf(HopeLinkTestSuite.class, testSuite);
    assertEquals("FactoryHopelink", testSuite.suiteName);
    assertEquals("https://factory-hopelink-otp.example.com", testSuite.otpWebUrl);
  }



  @Test
  public void testFactoryMethodWithInvalidClass() {
    var deploymentContext = new DeploymentContext(
      "TestDeployment",
      "https://test-otp.example.com"
    );
    
    // Test that factory method throws appropriate exception for invalid class
    assertThrows(RuntimeException.class, () -> {
      BaseTestSuite.createWithDeploymentContext(
        InvalidTestSuite.class,
        deploymentContext,
        "https://geocoding.example.com",
        47.61097,
        -122.33701
      );
    });
  }

  // Helper class for testing factory method error handling
  private static class InvalidTestSuite extends BaseTestSuite {
    // This class intentionally doesn't have the required constructor
    public InvalidTestSuite() {
      super("Invalid", "https://invalid.com", "https://geocoding.com", 0.0, 0.0);
    }
  }
}