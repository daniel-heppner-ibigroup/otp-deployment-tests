package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.config.DeploymentMetricsManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.ZoneId;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

public class TimedOtpApiClient extends OtpApiClient {

  private final MeterRegistry meterRegistry;
  private final String deploymentName;
  private final String testSuiteName;
  private final DeploymentMetricsManager metricsManager;

  /**
   * Constructor that accepts deployment context for enhanced metrics.
   *
   * @param zoneId The timezone for the OTP instance
   * @param webUrl The OTP web URL
   * @param meterRegistry The meter registry for metrics
   * @param deploymentName The name of the deployment
   * @param testSuiteName The name of the test suite
   * @param metricsManager The deployment metrics manager
   */
  public TimedOtpApiClient(
    ZoneId zoneId,
    String webUrl,
    MeterRegistry meterRegistry,
    String deploymentName,
    String testSuiteName,
    DeploymentMetricsManager metricsManager
  ) {
    super(zoneId, webUrl);
    this.meterRegistry = meterRegistry;
    this.deploymentName = deploymentName;
    this.testSuiteName = testSuiteName;
    this.metricsManager = metricsManager;
  }

  /**
   * Legacy constructor for backward compatibility.
   * This constructor is deprecated and should be replaced with the deployment context version.
   *
   * @param zoneId The timezone for the OTP instance
   * @param webUrl The OTP web URL
   * @param meterRegistry The meter registry for metrics
   * @param suiteName The name of the test suite (used as both deployment and suite name)
   * @deprecated Use constructor with DeploymentMetricsManager instead
   */
  @Deprecated
  public TimedOtpApiClient(
    ZoneId zoneId,
    String webUrl,
    MeterRegistry meterRegistry,
    String suiteName
  ) {
    super(zoneId, webUrl);
    this.meterRegistry = meterRegistry;
    this.deploymentName = suiteName;
    this.testSuiteName = suiteName;
    this.metricsManager = null; // Will fall back to legacy metrics
  }

  /**
   * Plans a trip with timing metrics that include deployment context.
   *
   * @param parameters The trip plan parameters
   * @param testName The name of the test making the request
   * @return The trip plan result
   * @throws IOException if the API request fails
   */
  public TripPlan timedPlan(TripPlanParameters parameters, String testName)
    throws IOException {
    if (metricsManager != null) {
      // Use new deployment-aware metrics
      Timer.Sample sample = metricsManager.startApiRequestTimer(
        deploymentName,
        testSuiteName,
        testName
      );
      try {
        TripPlan result = super.plan(parameters);
        metricsManager.stopApiRequestTimer(
          sample,
          deploymentName,
          testSuiteName,
          testName
        );
        return result;
      } catch (IOException e) {
        // Stop the timer even on failure
        metricsManager.stopApiRequestTimer(
          sample,
          deploymentName,
          testSuiteName,
          testName
        );
        throw e;
      }
    } else {
      // Fall back to legacy metrics for backward compatibility
      Timer.Sample sample = Timer.start(meterRegistry);
      TripPlan result = super.plan(parameters);
      Timer timer = meterRegistry.timer(
        MetricNames.planRequestTimer(testSuiteName, testName)
      );
      sample.stop(timer);
      return result;
    }
  }

  /**
   * Gets the deployment name for this client.
   *
   * @return The deployment name
   */
  public String getDeploymentName() {
    return deploymentName;
  }

  /**
   * Gets the test suite name for this client.
   *
   * @return The test suite name
   */
  public String getTestSuiteName() {
    return testSuiteName;
  }
}
