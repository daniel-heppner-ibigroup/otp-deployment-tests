package com.arcadis.otpsmoketests.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration parsing and validation.
 */
class DeploymentConfigurationTest {

    @Test
    void testConfigurationParsing() {
        // Create test configuration data
        Map<String, Object> properties = new HashMap<>();
        properties.put("otp.deployments.test.name", "Test Deployment");
        properties.put("otp.deployments.test.otp-url", "https://test-otp.example.com");
        properties.put("otp.deployments.test.test-suites[0].class-name", "com.example.TestSuite");
        properties.put("otp.deployments.test.test-suites[0].schedule", "0 */10 * * * *");
        properties.put("otp.deployments.test.test-suites[0].enabled", "true");

        // Create property source and binder
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        // Bind to configuration object
        DeploymentConfiguration config = binder.bind("otp", DeploymentConfiguration.class).get();

        // Verify parsing
        assertNotNull(config);
        assertEquals(1, config.getDeployments().size());
        
        DeploymentConfiguration.DeploymentConfig deployment = config.getDeployments().get("test");
        assertNotNull(deployment);
        assertEquals("Test Deployment", deployment.getName());
        assertEquals("https://test-otp.example.com", deployment.getOtpUrl());
        assertEquals(1, deployment.getTestSuites().size());
        
        DeploymentConfiguration.TestSuiteConfig testSuite = deployment.getTestSuites().get(0);
        assertEquals("com.example.TestSuite", testSuite.getClassName());
        assertEquals("0 */10 * * * *", testSuite.getSchedule());
        assertTrue(testSuite.isEnabled());
    }

    @Test
    void testConfigurationValidation() {
        ConfigurationValidator validator = new ConfigurationValidator();
        
        // Test valid URL
        assertTrue(validator.isValidUrl("https://example.com"));
        assertTrue(validator.isValidUrl("http://localhost:8080"));
        
        // Test invalid URLs
        assertFalse(validator.isValidUrl("not-a-url"));
        assertFalse(validator.isValidUrl("ftp://example.com"));
        assertFalse(validator.isValidUrl(""));
        assertFalse(validator.isValidUrl(null));
        
        // Test valid schedule expressions
        assertTrue(validator.isValidScheduleExpression("0 */10 * * * *")); // Cron
        assertTrue(validator.isValidScheduleExpression("10m")); // Simple interval
        assertTrue(validator.isValidScheduleExpression("30s")); // Simple interval
        assertTrue(validator.isValidScheduleExpression("2h")); // Simple interval
        
        // Test invalid schedule expressions
        assertFalse(validator.isValidScheduleExpression("invalid"));
        assertFalse(validator.isValidScheduleExpression(""));
        assertFalse(validator.isValidScheduleExpression(null));
    }

    @Test
    void testFullConfigurationValidation() {
        ConfigurationValidator validator = new ConfigurationValidator();
        DeploymentConfiguration config = new DeploymentConfiguration();
        
        // Test empty configuration
        List<String> errors = validator.validateConfiguration(config);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("No deployments configured"));
        
        // Test configuration with valid deployment
        DeploymentConfiguration.DeploymentConfig deployment = new DeploymentConfiguration.DeploymentConfig();
        deployment.setName("Test");
        deployment.setOtpUrl("https://example.com");
        
        DeploymentConfiguration.TestSuiteConfig testSuite = new DeploymentConfiguration.TestSuiteConfig();
        testSuite.setClassName("java.lang.String"); // Use existing class for test
        testSuite.setSchedule("0 */10 * * * *");
        testSuite.setEnabled(true);
        
        deployment.setTestSuites(List.of(testSuite));
        config.getDeployments().put("test", deployment);
        
        errors = validator.validateConfiguration(config);
        assertTrue(errors.isEmpty(), "Configuration should be valid but got errors: " + errors);
    }
}