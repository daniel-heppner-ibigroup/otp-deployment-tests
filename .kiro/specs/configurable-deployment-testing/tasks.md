# Implementation Plan

- [x] 1. Create configuration infrastructure
  - Create DeploymentConfiguration classes with Spring Boot configuration properties
  - Implement configuration validation logic
  - Add configuration file loading and parsing
  - _Requirements: 1.1, 1.2, 1.3, 6.1, 6.2_

- [x] 1.1 Create DeploymentConfiguration classes
  - Write DeploymentConfiguration class with @ConfigurationProperties annotation
  - Create nested DeploymentConfig and TestSuiteConfig classes
  - Add validation annotations and custom validators
  - _Requirements: 1.1, 1.2_

- [x] 1.2 Implement configuration validation
  - Create ConfigurationValidator class with validation logic
  - Add URL format validation for OTP endpoints
  - Add class name validation for test suites
  - Add schedule expression validation (cron format)
  - _Requirements: 1.4, 6.1, 6.2_

- [x] 1.3 Add configuration file support
  - Create application-deployments.yml configuration file
  - Add configuration loading in Spring Boot application
  - Test configuration parsing with sample data
  - _Requirements: 1.1, 1.3_

- [-] 2. Create deployment context system
  - Implement DeploymentContext class for runtime deployment information
  - Create TestExecutorFactory for creating deployment-specific test executors
  - Add TestSuiteExecutor for running tests with deployment context
  - _Requirements: 4.1, 4.2, 4.3_

- [x] 2.1 Implement DeploymentContext class
  - Create DeploymentContext with deployment name and OTP URL
  - Add utility methods for accessing deployment information
  - Write unit tests for DeploymentContext functionality
  - _Requirements: 4.1, 4.2_

- [x] 2.2 Create TestExecutorFactory
  - Implement factory pattern for creating test suite executors
  - Add logic to instantiate test suites with deployment context
  - Handle test suite class loading and instantiation errors
  - _Requirements: 4.3, 4.4, 6.2_

- [x] 2.3 Implement TestSuiteExecutor
  - Create executor class that runs JUnit tests with deployment context
  - Add test suite instantiation with deployment-specific parameters
  - Implement test execution result collection and reporting
  - _Requirements: 4.1, 4.2, 4.3_

- [x] 3. Implement dynamic scheduling system
  - Create ScheduleManager for managing test suite schedules
  - Add support for cron expression parsing and scheduling
  - Implement schedule updates and task cancellation
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3.1 Create ScheduleManager class
  - Implement ScheduleManager with TaskScheduler integration
  - Add methods for scheduling, rescheduling, and canceling tasks
  - Create task ID generation and tracking system
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3.2 Add cron expression support
  - Integrate Spring's cron expression parsing
  - Add validation for cron expressions in configuration
  - Implement fallback to default schedule for invalid expressions
  - _Requirements: 3.1, 3.4_

- [x] 3.3 Implement schedule management
  - Add logic to schedule test suites based on configuration
  - Implement independent scheduling per deployment and test suite
  - Add schedule update capability without application restart
  - _Requirements: 3.2, 3.3, 3.5_

- [x] 4. Update test suite architecture
  - Modify BaseTestSuite to support deployment context injection
  - Update existing test suites to use new constructor pattern
  - Remove hardcoded deployment configurations from test suites
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 4.1 Modify BaseTestSuite constructor
  - Update BaseTestSuite to accept deployment context
  - Maintain existing constructor for geocoding parameters
  - Add factory method for creating test suites with deployment context
  - _Requirements: 4.1, 4.2, 4.3_

- [x] 4.2 Update SoundTransitTestSuite
  - Remove hardcoded deployment URL from constructor
  - Update constructor to accept deployment context parameter
  - Keep geocoding URL and focus coordinates in test suite
  - Test that all existing tests still pass with new constructor
  - _Requirements: 4.1, 4.2, 4.4, 4.5_

- [x] 4.3 Update HopeLinkTestSuite
  - Remove hardcoded deployment URL from constructor
  - Update constructor to accept deployment context parameter
  - Keep geocoding URL and focus coordinates in test suite
  - Test that all existing tests still pass with new constructor
  - _Requirements: 4.1, 4.2, 4.4, 4.5_

- [ ] 5. Enhance metrics system
  - Update metrics to include deployment tags
  - Create DeploymentMetricsManager for deployment-specific metrics
  - Update existing metrics collection to use new tagging system
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 5.1 Create DeploymentMetricsManager
  - Implement metrics manager with deployment and test suite tagging
  - Add methods for recording test execution results with tags
  - Update metric naming convention to include deployment information
  - _Requirements: 5.1, 5.2, 5.4_

- [ ] 5.2 Update TimedOtpApiClient metrics
  - Modify TimedOtpApiClient to accept deployment context
  - Update metrics recording to include deployment tags
  - Ensure metrics are properly tagged for filtering and aggregation
  - _Requirements: 5.1, 5.2, 5.3_

- [ ] 5.3 Update TestRunner metrics integration
  - Modify TestRunner to use DeploymentMetricsManager
  - Update all metric recording calls to include deployment context
  - Test that metrics are properly tagged and accessible
  - _Requirements: 5.1, 5.2, 5.4, 5.5_

- [ ] 6. Replace TestRunner with configuration-driven system
  - Remove hardcoded test suite registration from TestRunner
  - Implement configuration-based test scheduling in main application
  - Update REST endpoints to work with dynamic deployments
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3_

- [ ] 6.1 Remove hardcoded test suites from TestRunner
  - Remove addTestSuite calls for SoundTransitTestSuite and HopeLinkTestSuite
  - Remove hardcoded test suite map and related initialization
  - Clean up unused metrics registration code
  - _Requirements: 2.1, 2.2_

- [ ] 6.2 Implement configuration-driven scheduling
  - Update OtpMonitoringApplication to use ScheduleManager
  - Add configuration loading and validation on application startup
  - Implement automatic scheduling of all configured test suites
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2_

- [ ] 6.3 Update REST endpoints
  - Modify /run-tests endpoint to support deployment-specific execution
  - Add endpoint to list configured deployments and schedules
  - Update manual test execution to work with deployment context
  - _Requirements: 2.3, 3.3_

- [ ] 7. Create configuration files and test deployment
  - Create configuration file with existing deployment settings
  - Test complete system with both SoundTransit and HopeLink deployments
  - Validate metrics collection and scheduling functionality
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 5.1, 5.2_

- [ ] 7.1 Create deployment configuration file
  - Write application-deployments.yml with SoundTransit and HopeLink configurations
  - Set appropriate schedules for each test suite per deployment
  - Validate configuration file format and content
  - _Requirements: 1.1, 1.2_

- [ ] 7.2 Integration testing
  - Test complete system startup with configuration loading
  - Verify all test suites are scheduled correctly
  - Test manual test execution via REST endpoints
  - Validate metrics are properly tagged and collected
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 5.1, 5.2_

- [ ] 7.3 End-to-end validation
  - Run system for full test cycle to ensure stability
  - Verify test execution results match previous behavior
  - Check metrics dashboard shows deployment-specific data
  - Validate configuration hot-reloading works correctly
  - _Requirements: 3.5, 5.3, 5.4, 5.5_