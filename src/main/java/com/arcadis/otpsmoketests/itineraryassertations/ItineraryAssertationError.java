package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.List;

public class ItineraryAssertationError extends AssertionError {

  private final List<SmokeTestItinerary.MatchResult> failedResults;

  public ItineraryAssertationError(
    String message,
    List<SmokeTestItinerary.MatchResult> failedResults
  ) {
    super(message);
    this.failedResults = failedResults;
  }

  public List<SmokeTestItinerary.MatchResult> getFailedResults() {
    return failedResults;
  }
}
