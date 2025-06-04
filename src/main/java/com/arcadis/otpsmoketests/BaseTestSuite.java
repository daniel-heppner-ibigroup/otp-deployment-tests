package com.arcadis.otpsmoketests;

import com.arcadis.otpsmoketests.geocoding.GeocodingService;
import com.arcadis.otpsmoketests.monitoringapp.TimedOtpApiClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.parameters.TripPlanParameters;
import org.opentripplanner.client.parameters.TripPlanParametersBuilder;

/**
 * Base class for OTP smoke tests, providing common setup for coordinates,
 * geocoding, and the OTP API client.
 *
 * <h3>Geocoding Cache</h3>
 * <p>Geocoding requests are cached globally across all test instances to improve performance.
 * This means that the first test suite to run will perform the geocoding HTTP requests,
 * and subsequent test suites will use the cached results. This significantly reduces
 * test execution time when running multiple test suites.
 *
 * <h3>Custom TripPlanParameters</h3>
 * <p>Test suites can customize their default TripPlanParameters by overriding
 * {@code getDefaultTripPlanParameters()}. Individual tests can create custom
 * parameters by using TripPlanParameters.builder() directly.
 *
 * <p>Example of customizing suite defaults:
 * <pre>{@code
 * @Override
 * protected TripPlanParameters getDefaultTripPlanParameters() {
 *   return TripPlanParameters
 *     .builder()
 *     .withNumberOfItineraries(10)
 *     .build();
 * }
 * }</pre>
 *
 * <p>Example of using TripPlanParameters directly in a test:
 * <pre>{@code
 * var params = TripPlanParameters
 *   .builder()
 *   .withFrom(geocoder.get("FROM_LOCATION"))
 *   .withTo(geocoder.get("TO_LOCATION"))
 *   .withModes(Set.of(TRANSIT, WALK))
 *   .withTime(weekdayAtNoon())
 *   .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
 *   .withNumberOfItineraries(20)
 *   .build();
 * var plan = apiClient.plan(params);
 * }</pre>
 */
public abstract class BaseTestSuite {

  protected static final MeterRegistry meterRegistry = Metrics.globalRegistry;

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
  protected BaseTestSuite(
    String suiteName,
    String otpWebUrl,
    String peliasBaseUrl,
    double lat,
    double lon
  ) {
    this.suiteName = suiteName;
    this.otpWebUrl = otpWebUrl;
    this.geocoder =
      GeocodingService
        .builder()
        .peliasBaseUrl(peliasBaseUrl)
        .focusPoint(lat, lon)
        .build();
    // Assuming all tests run against OTP instances configured for America/New_York
    // If this changes, this might need to become a parameter.
    this.apiClient =
      new TimedOtpApiClient(
        ZoneId.of("America/New_York"),
        otpWebUrl,
        meterRegistry,
        suiteName
      );
    // Initialize coordinates - geocoding requests are cached globally for performance
    initializeCoordinates();
  }

  /**
   * Abstract method for subclasses to implement their specific coordinate
   * initialization using the geocoder.
   */
  protected abstract void initializeCoordinates();

  /**
   * Creates default TripPlanParameters that can be used as a base for test suite customizations.
   *
   * Subclasses can override this method to provide suite-specific default parameters.
   *
   * @return Default TripPlanParameters that can be used as a starting point
   */
  protected TripPlanParameters getDefaultTripPlanParameters() {
    return TripPlanParameters.builder().build();
  }

  /**
   * Returns a LocalDateTime for the next Friday at noon.
   * This is commonly used for testing during weekday service hours.
   *
   * @return A LocalDateTime for the next Friday at noon
   */
  public static LocalDateTime weekdayAtNoon() {
    return weekdayAtTime(LocalTime.of(12, 0));
  }

  /**
   * Returns a LocalDateTime for the next Friday at the specified time.
   * This is commonly used for testing during weekday service hours.
   *
   * @param localTime The time of day to use
   * @return A LocalDateTime for the next Friday at the specified time
   */
  public static LocalDateTime weekdayAtTime(LocalTime localTime) {
    var today = LocalDate.now();
    return today
      .with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
      .atTime(localTime);
  }

  public static TripPlanParametersBuilder defaultBuilder() {
    return TripPlanParameters
      .builder()
      .withTime(weekdayAtNoon())
      .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
      .withModes(Set.of(RequestMode.TRANSIT, RequestMode.WALK));
  }

  /**
   * Clears the global geocoding cache. This can be useful for testing scenarios
   * where you want to force fresh geocoding requests.
   */
  protected static void clearGeocodingCache() {
    GeocodingService.clearGlobalCache();
  }

  /**
   * Gets the current size of the global geocoding cache.
   * 
   * @return The number of cached geocoded addresses
   */
  protected static int getGeocodingCacheSize() {
    return GeocodingService.getGlobalCacheSize();
  }

  // Common utility methods could be added here if needed in the future.
}
