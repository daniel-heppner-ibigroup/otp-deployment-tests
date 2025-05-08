package com.arcadis.otpsmoketests.monitoringapp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

import java.io.IOException;
import java.time.ZoneId;

public class TimedOtpApiClient extends OtpApiClient {
    private final MeterRegistry meterRegistry;
    private final String suiteName;

    public TimedOtpApiClient(ZoneId zoneId, String webUrl, MeterRegistry meterRegistry, String suiteName) {
        super(zoneId, webUrl);
        this.meterRegistry = meterRegistry;
        this.suiteName = suiteName;
    }

    public TripPlan timedPlan(TripPlanParameters parameters, String testName) throws IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        TripPlan result = super.plan(parameters);
        Timer timer = meterRegistry.timer(MetricNames.planRequestTimer(suiteName, testName));
        sample.stop(timer);
        return result;
    }
}