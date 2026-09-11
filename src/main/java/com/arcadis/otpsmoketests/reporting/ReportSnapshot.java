package com.arcadis.otpsmoketests.reporting;

import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opentripplanner.assertions.ItineraryAssertionError;
import org.opentripplanner.assertions.ItineraryMatchResult;
import org.opentripplanner.assertions.LegMatchingState;
import org.opentripplanner.client.model.FareProductUse;
import org.opentripplanner.client.model.Itinerary;
import org.opentripplanner.client.model.Leg;

/** Converts library objects into a stable, presentation-oriented report snapshot. */
public final class ReportSnapshot {

  private ReportSnapshot() {}

  public static Report from(
    String id,
    CustomTestRunner.SuiteResult suite,
    String deployment,
    String baseUrl
  ) {
    return new Report(
      id,
      suite.startedAt(),
      deployment,
      suite.suiteName(),
      baseUrl,
      suite.totalDurationMs(),
      suite.testResults().stream().map(ReportSnapshot::test).toList()
    );
  }

  private static Report.Test test(CustomTestRunner.TestResult test) {
    List<Report.Request> requests = new ArrayList<>(
      test.getRequests().stream().map(ReportSnapshot::request).toList()
    );
    Throwable failure = test.getException();
    Report.Expectation expectation = null;
    if (
      failure instanceof ItineraryAssertionError error &&
      error.getTripPlan() != null
    ) {
      int requestIndex = -1;
      for (int i = 0; i < test.getRequests().size(); i++) {
        if (test.getRequests().get(i).plan() == error.getTripPlan()) {
          requestIndex = i;
          break;
        }
      }
      if (requestIndex < 0) {
        // A test may assert on a derived plan rather than the exact response instance.
        requestIndex = requests.size();
        requests.add(
          new Report.Request(
            Map.of(
              "Source",
              "Assertion input; request parameters not captured"
            ),
            "Not captured",
            error
              .getTripPlan()
              .itineraries()
              .stream()
              .map(ReportSnapshot::journey)
              .toList(),
            null
          )
        );
      }
      List<Report.Match> matches = new ArrayList<>();
      for (int i = 0; i < error.getFailedResults().size(); i++) {
        matches.add(
          match(
            error.getFailedResults().get(i),
            error.getTripPlan().itineraries().get(i)
          )
        );
      }
      expectation =
        new Report.Expectation(
          requestIndex,
          error.getExpectedLegs(),
          error.isStrictTransitMatching(),
          matches
        );
    }
    String stackTrace = null;
    if (failure != null) {
      StringWriter text = new StringWriter();
      failure.printStackTrace(new PrintWriter(text));
      stackTrace = text.toString();
    }
    return new Report.Test(
      test.getDisplayName(),
      test.isPassed(),
      test.getDurationMs(),
      failure == null ? null : failure.getClass().getSimpleName(),
      failure == null ? null : failure.getMessage(),
      stackTrace,
      requests,
      expectation
    );
  }

  private static Report.Request request(TripCapture.CapturedRequest request) {
    return new Report.Request(
      request.parameters(),
      request.timeZone(),
      request.plan() == null
        ? List.of()
        : request
          .plan()
          .itineraries()
          .stream()
          .map(ReportSnapshot::journey)
          .toList(),
      request.error()
    );
  }

  private static Report.Journey journey(Itinerary itinerary) {
    return new Report.Journey(
      itinerary.legs().stream().map(ReportSnapshot::leg).toList()
    );
  }

  private static Report.Leg leg(Leg leg) {
    return new Report.Leg(
      leg.routeDisplayName(),
      leg.route() == null ? null : leg.route().getLongName(),
      String.valueOf(leg.mode()),
      leg.from() == null ? "Unknown origin" : leg.from().name(),
      leg.to() == null ? "Unknown destination" : leg.to().name(),
      value(leg.startTime()),
      value(leg.endTime()),
      value(leg.duration()),
      leg.interlineWithPreviousLeg(),
      leg.fareProducts() == null
        ? List.of()
        : leg.fareProducts().stream().map(ReportSnapshot::fare).toList()
    );
  }

  private static Report.Fare fare(FareProductUse use) {
    var fare = use.product();
    return new Report.Fare(
      fare.name(),
      fare.price() == null
        ? "Not supplied"
        : fare.price().amount().toPlainString(),
      fare.price() == null || fare.price().currency() == null
        ? ""
        : fare.price().currency().code(),
      fare
        .riderCategory()
        .map(r -> r.name() + " (" + r.id() + ")")
        .orElse("Not supplied"),
      fare
        .medium()
        .map(m -> m.name() + " (" + m.id() + ")")
        .orElse("Not supplied")
    );
  }

  private static Report.Match match(
    ItineraryMatchResult match,
    Itinerary itinerary
  ) {
    List<Report.LegMatch> legs = new ArrayList<>();
    for (LegMatchingState state : match.successfulMatches()) legs.add(
      legMatch(state, itinerary, "matched")
    );
    for (LegMatchingState state : match.getPartialMatches()) legs.add(
      legMatch(state, itinerary, "partial")
    );
    for (Leg extra : match.extraMatches()) legs.add(
      new Report.LegMatch(
        indexOf(itinerary, extra),
        "extra",
        "",
        "Unexpected transit leg"
      )
    );
    return new Report.Match(match.errors(), legs);
  }

  private static Report.LegMatch legMatch(
    LegMatchingState state,
    Itinerary itinerary,
    String status
  ) {
    return new Report.LegMatch(
      indexOf(itinerary, state.getLeg()),
      status,
      state.getMatchingCriteria(),
      state.getMissingCriteria()
    );
  }

  private static int indexOf(Itinerary itinerary, Leg leg) {
    for (int i = 0; i < itinerary.legs().size(); i++) if (
      itinerary.legs().get(i) == leg
    ) return i;
    return -1;
  }

  private static String value(Object value) {
    return value == null ? "Not supplied" : value.toString();
  }
}
