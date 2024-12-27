package com.arcadis.otp_deployment_tests;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
public class OtpMonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(OtpMonitoringApplication.class, args);
    }
}

@RestController
@Component
class TestRunner {
    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);
    private final MeterRegistry meterRegistry;
    private final Launcher launcher;
    private final SummaryGeneratingListener listener;

    public TestRunner(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.launcher = LauncherFactory.create();
        this.listener = new SummaryGeneratingListener();
        
        // Initialize base metrics
        Counter.builder("otp.tests.total")
               .description("Total number of OTP tests run")
               .register(meterRegistry);
        
        Counter.builder("otp.tests.failures")
               .description("Total number of OTP test failures")
               .register(meterRegistry);
        
        Timer.builder("otp.tests.duration")
             .description("Time taken to execute OTP tests")
             .register(meterRegistry);
    }

    @GetMapping("/run-tests")
    public Map<String, Object> runTestsManually() {
        return executeTests();
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void runScheduledTests() {
        executeTests();
    }

    private Map<String, Object> executeTests() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage("com.arcadis.otp_deployment_tests.testsuites"))
            .build();

        launcher.registerTestExecutionListeners(listener);

        Timer.Sample sample = Timer.start(meterRegistry);
        launcher.execute(request);
        
        TestExecutionSummary summary = listener.getSummary();
        sample.stop(meterRegistry.timer("otp.tests.duration"));

        // Record test execution metrics
        meterRegistry.counter("otp.tests.total").increment(summary.getTestsFoundCount());
        meterRegistry.counter("otp.tests.failures").increment(summary.getTestsFailedCount());

        // Record individual test results
        summary.getFailures().forEach(failure -> {
            String testClass = failure.getTestIdentifier().getSource()
                .map(source -> source.toString().split("\\[")[0])
                .orElse("unknown");
            
            meterRegistry.counter("otp.test.failure", "class", testClass).increment();
        });

        // Log summary
        logger.info("Tests found: {}, Succeeded: {}, Failed: {}, Skipped: {}",
            summary.getTestsFoundCount(),
            summary.getTestsSucceededCount(),
            summary.getTestsFailedCount(),
            summary.getTestsSkippedCount()
        );

        // Return summary for API endpoint
        Map<String, Object> result = new HashMap<>();
        result.put("testsFound", summary.getTestsFoundCount());
        result.put("testsSucceeded", summary.getTestsSucceededCount());
        result.put("testsFailed", summary.getTestsFailedCount());
        result.put("testsSkipped", summary.getTestsSkippedCount());
        return result;
    }
} 