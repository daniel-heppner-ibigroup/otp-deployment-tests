package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.config.DeploymentConfiguration;
import com.arcadis.otpsmoketests.config.DeploymentMetricsManager;
import com.arcadis.otpsmoketests.config.ScheduleManager;
import com.arcadis.otpsmoketests.config.TestExecutorFactory;
import com.arcadis.otpsmoketests.config.TestSuiteExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = "com.arcadis.otpsmoketests")
@EnableScheduling
@EnableConfigurationProperties
public class OtpMonitoringApplication {

  private static final Logger logger = LoggerFactory.getLogger(
    OtpMonitoringApplication.class
  );

  @Autowired
  private DeploymentConfiguration deploymentConfiguration;

  @Autowired
  private ScheduleManager scheduleManager;

  public static void main(String[] args) {
    SpringApplication.run(OtpMonitoringApplication.class, args);
  }

  @PostConstruct
  public void initializeScheduling() {
    logger.info("Initializing configuration-driven test scheduling");
    
    try {
      // Schedule all configured test suites
      scheduleManager.rescheduleAll(deploymentConfiguration);
      
      logger.info(
        "Successfully initialized scheduling for {} deployments with {} active schedules",
        deploymentConfiguration.getDeployments().size(),
        scheduleManager.getActiveTaskCount()
      );
    } catch (Exception e) {
      logger.error("Failed to initialize test scheduling: {}", e.getMessage(), e);
    }
  }
}

@RestController
@Component
class TestRunner {

  private static final Logger logger = LoggerFactory.getLogger(
    TestRunner.class
  );
  private final MeterRegistry meterRegistry;
  private final DeploymentMetricsManager metricsManager;
  private final ScheduleManager scheduleManager;
  private final DeploymentConfiguration deploymentConfiguration;
  private final TestExecutorFactory testExecutorFactory;

  public TestRunner(
    MeterRegistry meterRegistry,
    ScheduleManager scheduleManager,
    DeploymentConfiguration deploymentConfiguration,
    TestExecutorFactory testExecutorFactory
  ) {
    this.meterRegistry = meterRegistry;
    this.metricsManager = new DeploymentMetricsManager(meterRegistry);
    this.scheduleManager = scheduleManager;
    this.deploymentConfiguration = deploymentConfiguration;
    this.testExecutorFactory = testExecutorFactory;
  }



  @GetMapping("/run-tests")
  public Map<String, Object> runAllTestsManually() {
    logger.info("Manual execution of all configured test suites requested");
    
    Map<String, Object> result = new HashMap<>();
    Map<String, Object> deploymentResults = new HashMap<>();
    
    int totalTests = 0;
    int totalFailures = 0;
    
    for (Map.Entry<String, DeploymentConfiguration.DeploymentConfig> deploymentEntry : 
         deploymentConfiguration.getDeployments().entrySet()) {
      
      String deploymentName = deploymentEntry.getKey();
      DeploymentConfiguration.DeploymentConfig deployment = deploymentEntry.getValue();
      
      Map<String, Object> suiteResults = new HashMap<>();
      
      for (DeploymentConfiguration.TestSuiteConfig testSuiteConfig : deployment.getTestSuites()) {
        if (!testSuiteConfig.isEnabled()) {
          continue;
        }
        
        try {
          TestSuiteExecutor executor = testExecutorFactory.createExecutor(
            deploymentName, deployment, testSuiteConfig
          );
          
          var executionResult = executor.executeTests();
          suiteResults.put(testSuiteConfig.getClassName(), executionResult.toMap());
          
          totalTests += executionResult.getTestsRun();
          totalFailures += executionResult.getFailureCount();
          
        } catch (Exception e) {
          logger.error("Failed to execute test suite '{}' for deployment '{}': {}", 
                      testSuiteConfig.getClassName(), deploymentName, e.getMessage(), e);
          
          Map<String, Object> errorResult = new HashMap<>();
          errorResult.put("error", e.getMessage());
          errorResult.put("status", "failed");
          suiteResults.put(testSuiteConfig.getClassName(), errorResult);
        }
      }
      
      deploymentResults.put(deploymentName, suiteResults);
    }
    
    result.put("deployments", deploymentResults);
    result.put("totalTests", totalTests);
    result.put("totalFailures", totalFailures);
    result.put("status", totalFailures == 0 ? "success" : "failed");
    
    logger.info("Manual test execution completed. Total tests: {}, failures: {}", 
               totalTests, totalFailures);
    
    return result;
  }

  @PostMapping("/run-tests/{deploymentName}")
  public Map<String, Object> runDeploymentTestsManually(@PathVariable String deploymentName) {
    logger.info("Manual execution of test suites for deployment '{}' requested", deploymentName);
    
    DeploymentConfiguration.DeploymentConfig deployment = 
      deploymentConfiguration.getDeployments().get(deploymentName);
    
    if (deployment == null) {
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", "Deployment not found: " + deploymentName);
      errorResult.put("status", "not_found");
      return errorResult;
    }
    
    Map<String, Object> result = new HashMap<>();
    Map<String, Object> suiteResults = new HashMap<>();
    
    int totalTests = 0;
    int totalFailures = 0;
    
    for (DeploymentConfiguration.TestSuiteConfig testSuiteConfig : deployment.getTestSuites()) {
      if (!testSuiteConfig.isEnabled()) {
        continue;
      }
      
      try {
        TestSuiteExecutor executor = testExecutorFactory.createExecutor(
          deploymentName, deployment, testSuiteConfig
        );
        
        var executionResult = executor.executeTests();
        suiteResults.put(testSuiteConfig.getClassName(), executionResult.toMap());
        
        totalTests += executionResult.getTestsRun();
        totalFailures += executionResult.getFailureCount();
        
      } catch (Exception e) {
        logger.error("Failed to execute test suite '{}' for deployment '{}': {}", 
                    testSuiteConfig.getClassName(), deploymentName, e.getMessage(), e);
        
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", e.getMessage());
        errorResult.put("status", "failed");
        suiteResults.put(testSuiteConfig.getClassName(), errorResult);
      }
    }
    
    result.put("deployment", deploymentName);
    result.put("testSuites", suiteResults);
    result.put("totalTests", totalTests);
    result.put("totalFailures", totalFailures);
    result.put("status", totalFailures == 0 ? "success" : "failed");
    
    logger.info("Manual test execution for deployment '{}' completed. Total tests: {}, failures: {}", 
               deploymentName, totalTests, totalFailures);
    
    return result;
  }

  @PostMapping("/run-tests/{deploymentName}/{testSuiteClassName}")
  public Map<String, Object> runSpecificTestSuiteManually(
    @PathVariable String deploymentName,
    @PathVariable String testSuiteClassName
  ) {
    logger.info("Manual execution of test suite '{}' for deployment '{}' requested", 
               testSuiteClassName, deploymentName);
    
    DeploymentConfiguration.DeploymentConfig deployment = 
      deploymentConfiguration.getDeployments().get(deploymentName);
    
    if (deployment == null) {
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", "Deployment not found: " + deploymentName);
      errorResult.put("status", "not_found");
      return errorResult;
    }
    
    DeploymentConfiguration.TestSuiteConfig testSuiteConfig = deployment.getTestSuites()
      .stream()
      .filter(config -> config.getClassName().equals(testSuiteClassName))
      .findFirst()
      .orElse(null);
    
    if (testSuiteConfig == null) {
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", "Test suite not found: " + testSuiteClassName);
      errorResult.put("status", "not_found");
      return errorResult;
    }
    
    if (!testSuiteConfig.isEnabled()) {
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", "Test suite is disabled: " + testSuiteClassName);
      errorResult.put("status", "disabled");
      return errorResult;
    }
    
    try {
      TestSuiteExecutor executor = testExecutorFactory.createExecutor(
        deploymentName, deployment, testSuiteConfig
      );
      
      var executionResult = executor.executeTests();
      
      Map<String, Object> result = executionResult.toMap();
      result.put("deployment", deploymentName);
      result.put("testSuite", testSuiteClassName);
      
      logger.info("Manual test execution for '{}' on '{}' completed. Tests: {}, failures: {}", 
                 testSuiteClassName, deploymentName, 
                 executionResult.getTestsRun(), executionResult.getFailureCount());
      
      return result;
      
    } catch (Exception e) {
      logger.error("Failed to execute test suite '{}' for deployment '{}': {}", 
                  testSuiteClassName, deploymentName, e.getMessage(), e);
      
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("error", e.getMessage());
      errorResult.put("status", "failed");
      errorResult.put("deployment", deploymentName);
      errorResult.put("testSuite", testSuiteClassName);
      return errorResult;
    }
  }

  @GetMapping("/deployments")
  public Map<String, Object> listDeployments() {
    Map<String, Object> result = new HashMap<>();
    
    Map<String, Object> deployments = new HashMap<>();
    
    for (Map.Entry<String, DeploymentConfiguration.DeploymentConfig> entry : 
         deploymentConfiguration.getDeployments().entrySet()) {
      
      String deploymentName = entry.getKey();
      DeploymentConfiguration.DeploymentConfig deployment = entry.getValue();
      
      Map<String, Object> deploymentInfo = new HashMap<>();
      deploymentInfo.put("name", deployment.getName());
      deploymentInfo.put("otpUrl", deployment.getOtpUrl());
      
      List<Map<String, Object>> testSuites = deployment.getTestSuites()
        .stream()
        .map(testSuite -> {
          Map<String, Object> suiteInfo = new HashMap<>();
          suiteInfo.put("className", testSuite.getClassName());
          suiteInfo.put("schedule", testSuite.getSchedule());
          suiteInfo.put("enabled", testSuite.isEnabled());
          return suiteInfo;
        })
        .collect(Collectors.toList());
      
      deploymentInfo.put("testSuites", testSuites);
      deployments.put(deploymentName, deploymentInfo);
    }
    
    result.put("deployments", deployments);
    result.put("activeSchedules", scheduleManager.getActiveSchedules());
    result.put("totalDeployments", deploymentConfiguration.getDeployments().size());
    result.put("activeTaskCount", scheduleManager.getActiveTaskCount());
    
    return result;
  }

  @GetMapping("/schedules")
  public Map<String, Object> listSchedules() {
    Map<String, Object> result = new HashMap<>();
    result.put("activeSchedules", scheduleManager.getActiveSchedules());
    result.put("activeTaskCount", scheduleManager.getActiveTaskCount());
    return result;
  }
}
