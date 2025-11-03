package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.List;
import org.opentripplanner.client.model.Leg;

/**
 * Result of attempting to match an itinerary against all required leg criteria.
 */
public record LegMatchResult(
  List<LegMatchingState> successfulMatches,
  List<LegMatchingState> partialMatches,

  List<Leg> extraMatches,
  List<String> errors
) {
  public static LegMatchResult success(List<LegMatchingState> successfulMatches) {
    return new LegMatchResult(successfulMatches, List.of(), List.of(), List.of());
  }

  // Provide compatibility methods for existing code
  public boolean isSuccess() {
    return !successfulMatches.isEmpty() && extraMatches.isEmpty();
  }

  public List<LegMatchingState> getPartialMatches() {
    return partialMatches();
  }
}
