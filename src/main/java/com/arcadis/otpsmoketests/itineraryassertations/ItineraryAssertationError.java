package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.List;

public class ItineraryAssertationError extends AssertionError {

  private final List<ItineraryMatchResult> failedResults;

  public ItineraryAssertationError(
    String message,
    List<ItineraryMatchResult> failedResults
  ) {
    super(message);
    this.failedResults = failedResults;
  }

  public List<ItineraryMatchResult> getFailedResults() {
    return failedResults;
  }
}
