package com.arcadis.otpsmoketests.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for REST endpoints with the new configuration-driven system.
 * Tests that all endpoints work correctly with deployment-specific execution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("deployments")
public class RestEndpointIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testListDeploymentsEndpoint() throws Exception {
    var response = restTemplate.getForEntity("/deployments", String.class);
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Verify response structure
    assertTrue(result.containsKey("deployments"), "Response should contain deployments");
    assertTrue(result.containsKey("activeSchedules"), "Response should contain activeSchedules");
    assertTrue(result.containsKey("totalDeployments"), "Response should contain totalDeployments");
    assertTrue(result.containsKey("activeTaskCount"), "Response should contain activeTaskCount");
    
    // Verify deployment data
    @SuppressWarnings("unchecked")
    Map<String, Object> deployments = (Map<String, Object>) result.get("deployments");
    assertTrue(deployments.containsKey("sound-transit"), "Should have sound-transit deployment");
    assertTrue(deployments.containsKey("hopelink"), "Should have hopelink deployment");
    
    assertEquals(2, result.get("totalDeployments"), "Should have 2 deployments");
  }

  @Test
  public void testListSchedulesEndpoint() throws Exception {
    var response = restTemplate.getForEntity("/schedules", String.class);
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Verify response structure
    assertTrue(result.containsKey("activeSchedules"), "Response should contain activeSchedules");
    assertTrue(result.containsKey("activeTaskCount"), "Response should contain activeTaskCount");
    
    // Should have 3 active schedules (1 for sound-transit, 2 for hopelink)
    assertEquals(3, result.get("activeTaskCount"), "Should have 3 active tasks");
  }

  @Test
  public void testDeploymentSpecificEndpoint() throws Exception {
    // Test sound-transit deployment endpoint
    var response = restTemplate.postForEntity("/run-tests/sound-transit", null, String.class);
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Verify response structure
    assertTrue(result.containsKey("deployment"), "Response should contain deployment");
    assertTrue(result.containsKey("testSuites"), "Response should contain testSuites");
    assertTrue(result.containsKey("totalTests"), "Response should contain totalTests");
    assertTrue(result.containsKey("totalFailures"), "Response should contain totalFailures");
    assertTrue(result.containsKey("status"), "Response should contain status");
    
    assertEquals("sound-transit", result.get("deployment"), "Should be sound-transit deployment");
  }

  @Test
  public void testSpecificTestSuiteEndpoint() throws Exception {
    // Test specific test suite endpoint
    var response = restTemplate.postForEntity(
      "/run-tests/sound-transit/com.arcadis.otpsmoketests.tests.SoundTransitTestSuite", 
      null, 
      String.class
    );
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Verify response structure
    assertTrue(result.containsKey("deployment"), "Response should contain deployment");
    assertTrue(result.containsKey("testSuite"), "Response should contain testSuite");
    assertTrue(result.containsKey("testsRun"), "Response should contain testsRun");
    assertTrue(result.containsKey("status"), "Response should contain status");
    
    assertEquals("sound-transit", result.get("deployment"), "Should be sound-transit deployment");
    assertEquals("com.arcadis.otpsmoketests.tests.SoundTransitTestSuite", 
                result.get("testSuite"), "Should be SoundTransitTestSuite");
  }

  @Test
  public void testInvalidDeploymentEndpoint() throws Exception {
    // Test with invalid deployment name
    var response = restTemplate.postForEntity("/run-tests/invalid-deployment", null, String.class);
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Should return error response
    assertEquals("not_found", result.get("status"), "Should return not_found status");
    assertTrue(result.get("error").toString().contains("Deployment not found"), 
              "Should contain error message");
  }

  @Test
  public void testInvalidTestSuiteEndpoint() throws Exception {
    // Test with invalid test suite name
    var response = restTemplate.postForEntity(
      "/run-tests/sound-transit/com.invalid.TestSuite", 
      null, 
      String.class
    );
    
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    
    @SuppressWarnings("unchecked")
    Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
    
    // Should return error response
    assertEquals("not_found", result.get("status"), "Should return not_found status");
    assertTrue(result.get("error").toString().contains("Test suite not found"), 
              "Should contain error message");
  }
}