package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.List;

public class ItineraryAssertationError extends AssertionError {

  private final List<LegMatchResult> failedResults;

  public ItineraryAssertationError(
    String message,
    List<LegMatchResult> failedResults
  ) {
    super(message);
    this.failedResults = failedResults;
  }

  public List<LegMatchResult> getFailedResults() {
    return failedResults;
  }
}
