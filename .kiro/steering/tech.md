# Technology Stack

## Build System & Framework
- **Build Tool**: Maven 3.9+ with Java 21
- **Framework**: Spring Boot 3.2.1 with Spring Web and Actuator
- **Testing**: JUnit 5.11.2 with JUnit Platform Launcher
- **Containerization**: Docker with multi-stage builds using Eclipse Temurin

## Key Dependencies
- **OTP Client**: `org.opentripplanner:otp-client:0.1.13` for OpenTripPlanner API integration
- **Metrics**: Micrometer with Prometheus registry for monitoring
- **HTTP Client**: OkHttp 4.12.0 for external API calls
- **JSON Processing**: Jackson 2.16.1 for data serialization
- **Logging**: SLF4J 2.0.16 with Logstash Logback encoder for structured JSON logging

## Code Quality & Formatting
- **Code Formatting**: Prettier Java plugin (version 2.0.0) runs during validate phase
- **Encoding**: UTF-8 project encoding

## Common Commands

### Development
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Format code
mvn prettier:write

# Skip formatting during build
mvn clean package -Dplugin.prettier.skip=true

# Run application locally
mvn spring-boot:run
```

### Docker
```bash
# Build image
docker build -t otp-smoke-tests .

# Run container
docker run -p 8080:8080 otp-smoke-tests
```

### Application Endpoints
- Health check: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`
- Manual test run: `http://localhost:8080/run-tests`

## Configuration
- Application properties in `src/main/resources/application.properties`
- Structured JSON logging via Logback configuration
- Timezone: America/New_York for OTP API calls


# Special Note:
Please note that you cannot run commands because Kiro is not compatible with my shell configuration. I can run things myself in Intellij.