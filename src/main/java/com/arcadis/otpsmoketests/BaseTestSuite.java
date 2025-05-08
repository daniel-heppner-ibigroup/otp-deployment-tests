package com.arcadis.otpsmoketests;

import com.arcadis.otpsmoketests.geocoding.CoordinatesStore;
import com.arcadis.otpsmoketests.geocoding.GeocodingService;
import com.arcadis.otpsmoketests.monitoringapp.TimedOtpApiClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.ZoneId;

/**
 * Base class for OTP smoke tests, providing common setup for coordinates,
 * geocoding, and the OTP API client.
 */
public abstract class BaseTestSuite {

    protected static final MeterRegistry meterRegistry = Metrics.globalRegistry;
    protected static final CoordinatesStore COORDS = new CoordinatesStore();

    protected final GeocodingService geocoder;
    protected final TimedOtpApiClient apiClient;
    protected final String suiteName;
    protected final String otpWebUrl;

    /**
     * Constructor for BaseOtpSmokeTest.
     *
     * @param suiteName The name of the test suite (used for metrics).
     * @param otpWebUrl The base URL for the OTP instance being tested.
     */
    protected BaseTestSuite(String suiteName, String otpWebUrl) {
        this.suiteName = suiteName;
        this.otpWebUrl = otpWebUrl;
        this.geocoder = new GeocodingService(COORDS);
        // Assuming all tests run against OTP instances configured for America/New_York
        // If this changes, this might need to become a parameter.
        this.apiClient = new TimedOtpApiClient(
                ZoneId.of("America/New_York"),
                otpWebUrl,
                meterRegistry,
                suiteName
        );
        initializeCoordinates();
    }

    /**
     * Abstract method for subclasses to implement their specific coordinate
     * initialization using the geocoder.
     */
    protected abstract void initializeCoordinates();

    // Common utility methods could be added here if needed in the future.
} 