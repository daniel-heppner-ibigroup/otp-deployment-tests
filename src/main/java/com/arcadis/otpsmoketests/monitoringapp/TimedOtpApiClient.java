package com.arcadis.otpsmoketests.monitoringapp;

import com.arcadis.otpsmoketests.reporting.TripCapture;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.ZoneId;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

public class TimedOtpApiClient extends OtpApiClient {

  private final MeterRegistry meterRegistry;
  private final String suiteName;
  private final ZoneId zoneId;

  public TimedOtpApiClient(
    ZoneId zoneId,
    String webUrl,
    MeterRegistry meterRegistry,
    String suiteName
  ) {
    super(zoneId, webUrl);
    this.meterRegistry = meterRegistry;
    this.suiteName = suiteName;
    this.zoneId = zoneId;
  }

  @Override
  public TripPlan plan(TripPlanParameters parameters) throws IOException {
    TripPlan result = null;
    Throwable failure = null;
    try {
      result = super.plan(parameters);
      return result;
    } catch (IOException | RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      TripCapture.record(parameters, zoneId, result, failure);
    }
  }

  public TripPlan timedPlan(TripPlanParameters parameters, String testName)
    throws IOException {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return plan(parameters);
    } finally {
      Timer timer = meterRegistry.timer(
        MetricNames.planRequestTimer(suiteName, testName)
      );
      sample.stop(timer);
    }
  }
}
