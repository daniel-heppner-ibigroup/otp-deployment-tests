# Project Structure

## Package Organization
The project follows standard Maven directory structure with domain-driven package organization under `com.arcadis.otpsmoketests`:

```
src/main/java/com/arcadis/otpsmoketests/
├── BaseTestSuite.java                    # Abstract base class for all test suites
├── geocoding/                            # Geocoding service integration
│   └── GeocodingService.java            # Pelias geocoding with global caching
├── itineraryassertations/               # Test assertion utilities
│   ├── LegCriterion.java                # Criteria for matching trip legs
│   ├── LegMatchingState.java            # State management for leg matching
│   └── SmokeTestItinerary.java          # Fluent API for itinerary assertions
├── monitoringapp/                       # Spring Boot application and monitoring
│   ├── MetricNames.java                 # Prometheus metric name utilities
│   ├── OtpMonitoringApplication.java    # Main Spring Boot application
│   └── TimedOtpApiClient.java           # OTP client with metrics integration
└── tests/                               # Test suite implementations
    ├── HopeLinkTestSuite.java           # HopeLink flex transit tests
    └── SoundTransitTestSuite.java       # Sound Transit regular service tests
```

## Architecture Patterns

### Test Suite Pattern
- All test suites extend `BaseTestSuite` which provides:
  - Geocoding service with global caching
  - Timed OTP API client with metrics
  - Common utility methods for date/time handling
  - Default trip plan parameters

### Geocoding Strategy
- Global caching across all test instances for performance
- Supports both coordinate-based and text-based geocoding
- Pelias integration with focus point for regional accuracy

### Metrics Integration
- Each test suite gets its own metric namespace
- Automatic timing of OTP API calls
- Suite-specific and global counters for test results
- Prometheus-compatible metric names

### Assertion Framework
- Fluent API for itinerary validation (`SmokeTestItinerary`)
- Flexible leg matching with criteria-based assertions
- Support for route names, modes, and other trip characteristics

## Configuration Files
- `src/main/resources/application.properties` - Spring Boot configuration
- `src/main/resources/logback.xml` - Structured JSON logging setup
- `pom.xml` - Maven dependencies and build configuration
- `Dockerfile` - Multi-stage container build

## Naming Conventions
- Test classes end with `TestSuite` (not `Test` to distinguish from unit tests)
- Package names use lowercase
- Metric names follow Prometheus conventions with dots as separators
- Test methods use descriptive names reflecting the transit scenario being tested