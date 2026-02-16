package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.List;
import org.opentripplanner.client.model.Leg;

/**
 * Result of attempting to match an itinerary against all required leg criteria.
 * This is a container to store the results of matching different sets of leg criteria
 * against an itinerary.
 */
public record ItineraryMatchResult(
  List<LegMatchingState> successfulMatches,
  List<LegMatchingState> partialMatches,

  // Extra legs, only used in strictTransitMatching
  List<Leg> extraMatches,
  List<String> errors
) {
  public static ItineraryMatchResult success(List<LegMatchingState> successfulMatches) {
    return new ItineraryMatchResult(successfulMatches, List.of(), List.of(), List.of());
  }

  /***
   * @return Whether this LegMatchResult represents a successful match
   */
  public boolean isSuccess() {
    return !successfulMatches.isEmpty() && extraMatches.isEmpty() && partialMatches().isEmpty();
  }

  public List<LegMatchingState> getPartialMatches() {
    return partialMatches();
  }
}
