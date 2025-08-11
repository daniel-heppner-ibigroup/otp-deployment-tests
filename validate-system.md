# System Validation Guide

This document provides steps to validate the complete configurable deployment testing system.

## Prerequisites

1. Ensure the application is built successfully:
   ```bash
   mvn clean compile
   ```

2. Ensure all tests pass:
   ```bash
   mvn test
   ```

## Configuration Validation

### 1. Verify Configuration File
Check that `src/main/resources/application-deployments.yml` contains:
- Geocoding configuration with Pelias URL and focus coordinates
- Two deployments: `sound-transit` and `hopelink`
- Proper test suite mappings with valid class names and schedules

### 2. Verify Configuration Loading
Run the configuration integration test:
```bash
mvn test -Dtest=ConfigurationIntegrationTest
```

Expected results:
- All configuration properties are loaded correctly
- Geocoding configuration is present
- All deployments have valid test suite configurations

## System Startup Validation

### 1. Start the Application
```bash
mvn spring-boot:run
```

Expected behavior:
- Application starts without errors
- Configuration is loaded successfully
- All test suites are scheduled automatically
- Log messages indicate successful initialization

### 2. Check Startup Logs
Look for these log messages:
- "Initializing configuration-driven test scheduling"
- "Successfully initialized scheduling for 2 deployments with 3 active schedules"
- Scheduling messages for each test suite

## REST Endpoint Validation

### 1. List Deployments
```bash
curl http://localhost:8080/deployments
```

Expected response:
- JSON with `deployments`, `activeSchedules`, `totalDeployments`, `activeTaskCount`
- Two deployments: `sound-transit` and `hopelink`
- Three active schedules

### 2. List Active Schedules
```bash
curl http://localhost:8080/schedules
```

Expected response:
- JSON with `activeSchedules` and `activeTaskCount`
- Three active schedules:
  - `sound-transit-SoundTransitTestSuite`
  - `hopelink-HopeLinkTestSuite`
  - `hopelink-SoundTransitTestSuite`

### 3. Manual Test Execution
```bash
# Run all tests
curl -X POST http://localhost:8080/run-tests

# Run tests for specific deployment
curl -X POST http://localhost:8080/run-tests/sound-transit

# Run specific test suite
curl -X POST http://localhost:8080/run-tests/sound-transit/com.arcadis.otpsmoketests.tests.SoundTransitTestSuite
```

Expected behavior:
- Tests execute successfully (may fail due to network connectivity, but should not error)
- Response includes execution results with proper deployment tagging
- Metrics are recorded with deployment-specific tags

## Metrics Validation

### 1. Check Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

Expected metrics:
- Test execution metrics with deployment tags
- OTP API call metrics with deployment tags
- Metrics should be properly namespaced and tagged

### 2. Check Micrometer Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

Expected behavior:
- Metrics endpoint is available
- Custom metrics are registered
- Deployment-specific metrics are present

## Scheduling Validation

### 1. Verify Scheduled Execution
Wait for scheduled test execution (SoundTransit runs every 10 minutes, HopeLink every 15 minutes).

Expected behavior:
- Tests execute automatically according to their schedules
- Log messages indicate scheduled execution
- Metrics are updated after each execution

### 2. Test Schedule Management
The system should handle:
- Multiple independent schedules
- Different cron expressions
- Schedule updates without restart

## Error Handling Validation

### 1. Test Invalid Requests
```bash
# Invalid deployment
curl -X POST http://localhost:8080/run-tests/invalid-deployment

# Invalid test suite
curl -X POST http://localhost:8080/run-tests/sound-transit/com.invalid.TestSuite
```

Expected behavior:
- Proper error responses with appropriate status codes
- Error messages are descriptive
- System continues to operate normally

### 2. Test Configuration Resilience
The system should handle:
- Invalid configuration gracefully
- Missing test suite classes
- Invalid schedule expressions
- Network connectivity issues

## Performance Validation

### 1. Resource Usage
Monitor system resource usage during operation:
- Memory usage should be stable
- CPU usage should be reasonable
- No memory leaks during long-running operation

### 2. Concurrent Execution
The system should handle:
- Multiple test suites running simultaneously
- Overlapping scheduled executions
- Manual test execution during scheduled runs

## Integration Test Validation

Run all integration tests:
```bash
mvn test -Dtest=*IntegrationTest
mvn test -Dtest=EndToEndValidationTest
```

Expected results:
- All integration tests pass
- System components work together correctly
- Configuration-driven behavior is validated

## Success Criteria

The system is successfully validated when:

1. ✅ Configuration loads correctly from YAML file
2. ✅ All test suites can be instantiated with deployment context
3. ✅ Scheduling system manages multiple independent schedules
4. ✅ REST endpoints work with deployment-specific execution
5. ✅ Metrics are properly tagged with deployment information
6. ✅ Error handling is robust and graceful
7. ✅ System performance is stable under normal operation
8. ✅ Integration tests pass consistently

## Troubleshooting

### Common Issues

1. **Configuration not loading**: Check `application.properties` includes `spring.profiles.include=deployments`
2. **Test suite instantiation fails**: Verify geocoding configuration is present
3. **Scheduling not working**: Check TaskScheduler bean is configured
4. **Metrics not appearing**: Verify MeterRegistry is properly configured
5. **REST endpoints not working**: Check Spring Boot web configuration

### Debug Steps

1. Enable debug logging: `logging.level.com.arcadis.otpsmoketests=DEBUG`
2. Check application startup logs for errors
3. Verify configuration properties are bound correctly
4. Test individual components in isolation
5. Use integration tests to identify specific issues