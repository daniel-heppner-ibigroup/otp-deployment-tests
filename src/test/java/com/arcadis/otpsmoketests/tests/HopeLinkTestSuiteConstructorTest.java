package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.config.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify HopeLinkTestSuite constructor works correctly.
 */
public class HopeLinkTestSuiteConstructorTest {

  @Test
  public void testDeploymentContextConstructor() {
    // Test that the deployment context constructor works
    assertDoesNotThrow(() -> {
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
    });
  }

  @Test
  public void testFactoryMethod() {
    // Test that the factory method works
    assertDoesNotThrow(() -> {
      var deploymentContext = new DeploymentContext(
        "FactoryTestHopelink",
        "https://factory-test-hopelink-otp.example.com"
      );
      var testSuite = HopeLinkTestSuite.createWithDeploymentContext(
        HopeLinkTestSuite.class,
        deploymentContext,
        "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
        47.61097,
        -122.33701
      );
      assertNotNull(testSuite);
    });
  }
}