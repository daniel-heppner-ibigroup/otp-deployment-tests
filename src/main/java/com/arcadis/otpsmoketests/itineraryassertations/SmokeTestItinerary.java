package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.client.model.Itinerary;
import org.opentripplanner.client.model.Leg;
import org.opentripplanner.client.model.TripPlan;

/**
 * A fluent API for testing OTP itineraries against specific criteria.
 *
 * <p>This class allows you to specify required legs with various criteria and validates
 * that at least one itinerary in the trip plan matches all those criteria.
 *
 * <h3>Basic Usage:</h3>
 * <pre>
 * SmokeTestItinerary.from(tripPlan)
 *   .hasLeg()
 *   .withRouteShortName("1-Line")
 *   .assertMatches();
 * </pre>
 *
 * <h3>Strict Transit Matching:</h3>
 * <p>By default, the matcher allows additional transit legs beyond those specified.
 * Use {@code withStrictTransitMatching()} to require that the itinerary contains
 * ONLY the specified transit legs and no others.
 *
 * <p>Example - this will PASS if there's an itinerary with exactly one transit leg (the 1-Line):
 * <pre>
 * SmokeTestItinerary.from(tripPlan)
 *   .withStrictTransitMatching()
 *   .hasLeg()
 *   .withRouteShortName("1-Line")
 *   .assertMatches();
 * </pre>
 *
 * <p>Example - this will FAIL if the matching itinerary also contains other transit legs:
 * <pre>
 * // Fails if itinerary has "1-Line" + "E Line" (strict matching rejects extra transit legs)
 * SmokeTestItinerary.from(tripPlan)
 *   .withStrictTransitMatching()
 *   .hasLeg()
 *   .withRouteShortName("1-Line")
 *   .assertMatches();
 * </pre>
 *
 * <p>Example - multiple required legs with strict matching:
 * <pre>
 * // This requires exactly two transit legs: 1-Line and E Line, no others
 * SmokeTestItinerary.from(tripPlan)
 *   .withStrictTransitMatching()
 *   .hasLeg()
 *   .withRouteShortName("1-Line")
 *   .hasLeg()
 *   .withRouteShortName("E Line")
 *   .assertMatches();
 * </pre>
 *
 * <p>Note: Walking legs and other non-transit legs are not affected by strict matching.
 */
public class SmokeTestItinerary {

  // Each item in this list is a list that represents the complete list of criteria that a leg
  // needs to match in order to pass. For each item, there must be at least one leg that matches all the
  // criteria in the second list.
  private final List<List<LegCriterion>> legCriteria = new ArrayList<>();
  private List<LegCriterion> currentLegCriteria;
  private final TripPlan tripPlan;
  private boolean strictTransitMatching = false;

  public SmokeTestItinerary(TripPlan tripPlan) {
    this.tripPlan = tripPlan;
  }

  public static SmokeTestItinerary from(TripPlan tripPlan) {
    return new SmokeTestItinerary(tripPlan);
  }

  public SmokeTestItinerary hasLeg() {
    currentLegCriteria = new ArrayList<>();
    legCriteria.add(currentLegCriteria);
    return this;
  }

  public SmokeTestItinerary withRouteLongName(String... longNames) {
    var message = "route '" + Arrays.toString(longNames) + "'";
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches =
            leg.isTransit() &&
            leg.route().longName().isPresent() &&
            Arrays
              .stream(longNames)
              .anyMatch(longName ->
                Objects.equals(longName, leg.route().longName().get())
              );
          if (matches) {
            state.addMatch(message);
          } else {
            state.addFailure(message);
          }
        }
      )
    );
    return this;
  }

  public SmokeTestItinerary withRouteShortName(String... shortNames) {
    var message = "route '" + Arrays.toString(shortNames) + "'";
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches =
            leg.isTransit() &&
            leg.route().shortName().isPresent() &&
            Arrays
              .stream(shortNames)
              .anyMatch(shortName ->
                Objects.equals(shortName, leg.route().shortName().get())
              );
          if (matches) {
            state.addMatch(message);
          } else {
            state.addFailure(message);
          }
        }
      )
    );
    return this;
  }

  public SmokeTestItinerary withFarePrice(
    float price,
    String riderCategoryId,
    String mediumId
  ) {
    var message = "fare $" + price;
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches = leg
            .fareProducts()
            .stream()
            .filter(fp -> fp.product().riderCategory().isPresent())
            .filter(fp -> fp.product().medium().isPresent())
            .filter(fp ->
              fp.product().riderCategory().get().id().equals(riderCategoryId)
            )
            .filter(fp -> fp.product().medium().get().id().equals(mediumId))
            .anyMatch(fp -> fp.product().price().amount().floatValue() == price
            );
          if (matches) {
            state.addMatch("fare $" + price);
          } else {
            state.addFailure("fare $" + price);
          }
        }
      )
    );
    return this;
  }

  public SmokeTestItinerary interlinedWithPreviousLeg() {
    currentLegCriteria.add(
      new LegCriterion(
        "interlined with previous leg",
        state -> {
          Leg leg = state.getLeg();
          if (leg.interlineWithPreviousLeg()) {
            state.addMatch("interlined with previous leg");
          } else {
            state.addFailure("interlined with previous leg");
          }
        }
      )
    );
    return this;
  }

  public SmokeTestItinerary withMode(String mode) {
    var message = "mode " + mode;
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches = leg.mode().toString().equals(mode);
          if (matches) {
            state.addMatch("mode " + mode);
          } else {
            state.addFailure("mode " + mode);
          }
        }
      )
    );
    return this;
  }

  public SmokeTestItinerary withStrictTransitMatching() {
    this.strictTransitMatching = true;
    return this;
  }

  public void assertMatches() {
    List<MatchResult> failedResults = new ArrayList<>();

    for (Itinerary itinerary : tripPlan.itineraries()) {
      MatchResult result = matchesAllLegs(itinerary);
      if (result.isSuccess()) {
        return; // Found an itinerary that matches all leg criteria
      }
      failedResults.add(result);
    }

    // If we get here, no itinerary matched all legs
    StringBuilder error = new StringBuilder(
      "No itinerary found matching all required legs"
    );
    if (strictTransitMatching) {
      error.append(" with strict transit matching");
    }
    error.append(":\n");

    for (int i = 0; i < legCriteria.size(); i++) {
      error.append("Leg ").append(i + 1).append(" criteria:\n");
      error.append(describeCriteria(legCriteria.get(i))).append("\n");
    }

    error.append("\nFailures by itinerary:\n");
    for (int i = 0; i < failedResults.size(); i++) {
      MatchResult result = failedResults.get(i);
      error.append("Itinerary ").append(i + 1).append(":\n");

      for (String err : result.getErrors()) {
        error.append("  - ").append(err).append("\n");
      }

      if (!result.getPartialMatches().isEmpty()) {
        error.append("  Partial matches:\n");
        for (LegMatchingState match : result.getPartialMatches()) {
          error
            .append("    - Leg with ")
            .append(match.getMatchingCriteria())
            .append(" but missing ")
            .append(match.getMissingCriteria())
            .append("\n");
        }
      }
    }

    throw new AssertionError(error.toString());
  }

  private MatchResult matchesAllLegs(Itinerary itinerary) {
    List<Leg> remainingLegs = new ArrayList<>(itinerary.legs());
    List<String> errors = new ArrayList<>();
    List<LegMatchingState> partialMatches = new ArrayList<>();

    if (legCriteria.isEmpty()) {
      throw new IllegalArgumentException("No leg criteria specified");
    }

    // Try to find a match for each set of criteria
    for (
      int criteriaIndex = 0;
      criteriaIndex < legCriteria.size();
      criteriaIndex++
    ) {
      List<LegCriterion> criteriaSet = legCriteria.get(criteriaIndex);
      boolean foundMatch = false;

      if (criteriaSet.isEmpty()) {
        throw new IllegalArgumentException(
          "No leg criteria specified for criteria set " + (criteriaIndex + 1)
        );
      }

      // Look through remaining legs for one that matches this criteria set
      for (int i = 0; i < remainingLegs.size(); i++) {
        Leg leg = remainingLegs.get(i);
        LegMatchingState state = new LegMatchingState(leg);
        criteriaSet.forEach(criterion -> criterion.test().accept(state));

        if (state.isFullMatch()) {
          remainingLegs.remove(i); // Remove the matched leg so it can't be matched again
          foundMatch = true;
          break;
        } else if (state.hasAnyMatch()) {
          // Keep track of partial matches for error reporting
          partialMatches.add(state);
        }
      }

      if (!foundMatch) {
        errors.add(
          "No leg found matching criteria set " +
          (criteriaIndex + 1) +
          ": " +
          describeCriteria(criteriaSet).trim()
        );
      }
    }

    // If strict transit matching is enabled, check that no additional transit legs remain
    if (strictTransitMatching && errors.isEmpty()) {
      List<Leg> additionalTransitLegs = remainingLegs
        .stream()
        .filter(Leg::isTransit)
        .toList();

      if (!additionalTransitLegs.isEmpty()) {
        StringBuilder error = new StringBuilder(
          "Itinerary contains additional transit legs when strict matching is enabled: "
        );
        for (Leg leg : additionalTransitLegs) {
          if (leg.route().shortName().isPresent()) {
            error.append(leg.route().shortName().get()).append(" ");
          } else if (leg.route().longName().isPresent()) {
            error.append(leg.route().longName().get()).append(" ");
          } else {
            error.append(leg.mode().toString()).append(" ");
          }
        }
        errors.add(error.toString());
      }
    }

    if (errors.isEmpty()) {
      return MatchResult.success();
    } else {
      return MatchResult.failure(errors, partialMatches);
    }
  }

  private String describeCriteria(List<LegCriterion> criteriaSet) {
    StringBuilder message = new StringBuilder();
    for (LegCriterion criterion : criteriaSet) {
      message.append(criterion.message()).append("\n");
    }
    return message.toString();
  }

  /**
   * Result of attempting to match an itinerary against all required leg criteria.
   */
  private static class MatchResult {

    private final boolean success;
    private final List<String> errors;
    private final List<LegMatchingState> partialMatches;

    private MatchResult(
      boolean success,
      List<String> errors,
      List<LegMatchingState> partialMatches
    ) {
      this.success = success;
      this.errors = new ArrayList<>(errors);
      this.partialMatches = new ArrayList<>(partialMatches);
    }

    public static MatchResult success() {
      return new MatchResult(true, List.of(), List.of());
    }

    public static MatchResult failure(
      List<String> errors,
      List<LegMatchingState> partialMatches
    ) {
      return new MatchResult(false, errors, partialMatches);
    }

    public boolean isSuccess() {
      return success;
    }

    public List<String> getErrors() {
      return errors;
    }

    public List<LegMatchingState> getPartialMatches() {
      return partialMatches;
    }
  }
}
