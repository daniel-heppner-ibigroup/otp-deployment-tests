# Product Overview

This is an OTP (OpenTripPlanner) smoke testing and monitoring application for Arcadis. The system performs automated smoke tests against OTP instances to validate transit routing functionality and provides monitoring capabilities with Prometheus metrics.

## Key Features

- **Automated Smoke Testing**: Runs JUnit-based test suites against OTP APIs to validate routing functionality
- **Monitoring & Metrics**: Exposes Prometheus metrics for test execution results, timing, and failures
- **Multi-Suite Support**: Currently supports HopeLink and Sound Transit test suites
- **Geocoding Integration**: Uses Pelias geocoding service with caching for performance
- **Flexible Transit Testing**: Tests various transit modes including flex services, regular transit, and walking
- **Spring Boot Application**: Provides REST endpoints and scheduled test execution

## Test Suites

- **HopeLink**: Tests flex transit services, volunteer transportation, and specialized routes
- **Sound Transit**: Tests regular transit services and connections

The application runs tests every 10 minutes via scheduled execution and exposes results through REST endpoints and Prometheus metrics.