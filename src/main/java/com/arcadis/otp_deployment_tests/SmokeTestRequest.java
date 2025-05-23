package com.arcadis.otp_deployment_tests;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.Coordinate;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

public record SmokeTestRequest(
  Coordinate from,
  Coordinate to,
  Set<RequestMode> modes,
  boolean arriveBy,
  OtpApiClient apiClient
) {
  public SmokeTestRequest(
    Coordinate from,
    Coordinate to,
    Set<RequestMode> modes,
    OtpApiClient apiClient
  ) {
    this(from, to, modes, false, apiClient);
  }

  public TripPlanParameters.SearchDirection searchDirection() {
    if (arriveBy) {
      return TripPlanParameters.SearchDirection.ARRIVE_BY;
    } else {
      return TripPlanParameters.SearchDirection.DEPART_AT;
    }
  }

  public static LocalDateTime weekdayAtNoon() {
    return weekdayAtTime(LocalTime.of(12, 0));
  }

  public static LocalDateTime weekdayAtTime(LocalTime localTime) {
    var today = LocalDate.now();
    return today
      .with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
      .atTime(localTime);
  }

  public static TripPlan basicTripPlan(SmokeTestRequest req)
    throws IOException {
    var tpr = TripPlanParameters
      .builder()
      .withFrom(req.from())
      .withTo(req.to())
      .withModes(req.modes())
      .withTime(weekdayAtNoon())
      .withSearchDirection(req.searchDirection())
      .build();
    return req.apiClient().plan(tpr);
  }
}
