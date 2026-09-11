package com.arcadis.otpsmoketests.reporting;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persisted report data; independent of exception serialization and OTP model versions. */
public record Report(
  String id,
  Instant startedAt,
  String deployment,
  String suite,
  String baseUrl,
  long durationMs,
  List<Test> tests
) {
  public long failedCount() {
    return tests.stream().filter(test -> !test.passed()).count();
  }

  public record Test(
    String name,
    boolean passed,
    long durationMs,
    String errorType,
    String errorMessage,
    String stackTrace,
    List<Request> requests,
    Expectation expectation
  ) {}

  public record Request(
    Map<String, String> parameters,
    String timeZone,
    List<Journey> itineraries,
    String error
  ) {}

  /** requestIndex links the failed assertion to its response, never a guessed latest request. */
  public record Expectation(
    int requestIndex,
    List<List<String>> legs,
    boolean strictTransitMatching,
    List<Match> matches
  ) {}

  public record Match(List<String> errors, List<LegMatch> legs) {}

  public record LegMatch(
    int legIndex,
    String status,
    String matched,
    String missing
  ) {}

  public record Journey(List<Leg> legs) {}

  public record Leg(
    String route,
    String routeLongName,
    String mode,
    String from,
    String to,
    String departure,
    String arrival,
    String duration,
    boolean interlined,
    List<Fare> fares
  ) {}

  public record Fare(
    String name,
    String amount,
    String currency,
    String rider,
    String medium
  ) {}
}
