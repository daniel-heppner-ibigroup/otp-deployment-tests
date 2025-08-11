package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.config.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify SoundTransitTestSuite constructor works correctly.
 */
public class SoundTransitTestSuiteConstructorTest {

  @Test
  public void testDeploymentContextConstructor() {
    // Test that the deployment context constructor works
    assertDoesNotThrow(() -> {
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
    });
  }

  @Test
  public void testFactoryMethod() {
    // Test that the factory method works
    assertDoesNotThrow(() -> {
      var deploymentContext = new DeploymentContext(
        "FactoryTestSoundTransit",
        "https://factory-test-sound-transit-otp.example.com"
      );
      var testSuite = SoundTransitTestSuite.createWithDeploymentContext(
        SoundTransitTestSuite.class,
        deploymentContext,
        "https://87sp37ezga.execute-api.us-east-1.amazonaws.com/st/autocomplete",
        47.61097,
        -122.33701
      );
      assertNotNull(testSuite);
    });
  }
}