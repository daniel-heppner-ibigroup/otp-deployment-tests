package com.arcadis.otpsmoketests.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for configuration loading.
 */
@SpringBootTest
@ActiveProfiles("deployments")
class ConfigurationIntegrationTest {

    @Autowired
    private DeploymentConfiguration deploymentConfiguration;

    @Autowired
    private ConfigurationValidator configurationValidator;

    @Autowired
    private ConfigurationLoader configurationLoader;

    @Test
    void testConfigurationLoading() {
        assertNotNull(deploymentConfiguration);
        assertNotNull(configurationValidator);
        assertNotNull(configurationLoader);
        
        // Verify that configuration is loaded from application-deployments.yml
        assertFalse(deploymentConfiguration.getDeployments().isEmpty());
        
        // Check that we have the expected deployments
        assertTrue(deploymentConfiguration.getDeployments().containsKey("sound-transit"));
        assertTrue(deploymentConfiguration.getDeployments().containsKey("hopelink"));
        
        // Verify deployment details
        DeploymentConfiguration.DeploymentConfig soundTransit = 
            deploymentConfiguration.getDeployments().get("sound-transit");
        assertEquals("SoundTransit", soundTransit.getName());
        assertEquals("https://sound-transit-otp.ibi-transit.com", soundTransit.getOtpUrl());
        assertFalse(soundTransit.getTestSuites().isEmpty());
        
        DeploymentConfiguration.DeploymentConfig hopelink = 
            deploymentConfiguration.getDeployments().get("hopelink");
        assertEquals("Hopelink", hopelink.getName());
        assertEquals("https://hopelink-otp.ibi-transit.com", hopelink.getOtpUrl());
        assertFalse(hopelink.getTestSuites().isEmpty());
    }

    @Test
    void testConfigurationValidation() {
        // Test that the loaded configuration is valid
        var errors = configurationValidator.validateConfiguration(deploymentConfiguration);
        
        // Note: Some validation errors may occur due to test suite classes not being found
        // in test context, but URL and schedule validation should pass
        assertTrue(errors.stream().noneMatch(error -> error.contains("invalid OTP URL format")));
        assertTrue(errors.stream().noneMatch(error -> error.contains("invalid schedule expression")));
    }
}