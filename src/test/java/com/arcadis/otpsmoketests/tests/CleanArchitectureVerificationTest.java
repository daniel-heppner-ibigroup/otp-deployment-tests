package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.config.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test to ensure the clean architecture without backward compatibility works correctly.
 */
public class CleanArchitectureVerificationTest {

  @Test
  public void testSoundTransitTestSuiteRequiresDeploymentContext() {
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
    assertEquals("TestSoundTransit", testSuite.suiteName);
    assertEquals("https://test-sound-transit-otp.example.com", testSuite.otpWebUrl);
  }

  @Test
  public void testHopeLinkTestSuiteRequiresDeploymentContext() {
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
    assertEquals("TestHopelink", testSuite.suiteName);
    assertEquals("https://test-hopelink-otp.example.com", testSuite.otpWebUrl);
  }

  @Test
  public void testFactoryMethodWorksWithCleanArchitecture() {
    var deploymentContext = new DeploymentContext(
      "FactoryTest",
      "https://factory-test-otp.example.com"
    );
    
    var soundTransitSuite = BaseTestSuite.createWithDeploymentContext(
      SoundTransitTestSuite.class,
      deploymentContext,
      "https://87sp37ezga.execute-api.us-east-1.amazonaws.com/st/autocomplete",
      47.61097,
      -122.33701
    );
    
    var hopeLinkSuite = BaseTestSuite.createWithDeploymentContext(
      HopeLinkTestSuite.class,
      deploymentContext,
      "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
      47.61097,
      -122.33701
    );
    
    assertNotNull(soundTransitSuite);
    assertNotNull(hopeLinkSuite);
    assertInstanceOf(SoundTransitTestSuite.class, soundTransitSuite);
    assertInstanceOf(HopeLinkTestSuite.class, hopeLinkSuite);
    
    assertEquals("FactoryTest", soundTransitSuite.suiteName);
    assertEquals("FactoryTest", hopeLinkSuite.suiteName);
    assertEquals("https://factory-test-otp.example.com", soundTransitSuite.otpWebUrl);
    assertEquals("https://factory-test-otp.example.com", hopeLinkSuite.otpWebUrl);
  }
}