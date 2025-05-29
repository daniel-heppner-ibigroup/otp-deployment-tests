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
    List<List<LegMatchingState>> partialMatchesByLeg = new ArrayList<>();
    List<String> strictMatchingFailures = new ArrayList<>();

    for (Itinerary itinerary : tripPlan.itineraries()) {
      if (matchesAllLegs(itinerary)) {
        return; // Found an itinerary that matches all leg criteria
      }

      // Store partial matches for error reporting
      List<LegMatchingState> partialMatches = findPartialMatches(itinerary);
      if (!partialMatches.isEmpty()) {
        partialMatchesByLeg.add(partialMatches);
      }

      // Check for strict matching failures
      if (strictTransitMatching && hasAllRequiredLegs(itinerary)) {
        List<Leg> additionalTransitLegs = findAdditionalTransitLegs(itinerary);
        if (!additionalTransitLegs.isEmpty()) {
          StringBuilder failure = new StringBuilder("Itinerary matches all required legs but contains additional transit legs: ");
          for (Leg leg : additionalTransitLegs) {
            if (leg.route().shortName().isPresent()) {
              failure.append(leg.route().shortName().get()).append(" ");
            } else if (leg.route().longName().isPresent()) {
              failure.append(leg.route().longName().get()).append(" ");
            } else {
              failure.append(leg.mode().toString()).append(" ");
            }
          }
          strictMatchingFailures.add(failure.toString());
        }
      }
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

    if (!strictMatchingFailures.isEmpty()) {
      error.append("\nStrict matching failures:\n");
      for (String failure : strictMatchingFailures) {
        error.append("- ").append(failure).append("\n");
      }
    }

    if (!partialMatchesByLeg.isEmpty()) {
      error.append("\nPartial matches found in itineraries:\n");
      for (int i = 0; i < partialMatchesByLeg.size(); i++) {
        error.append("Itinerary ").append(i + 1).append(":\n");
        List<LegMatchingState> matches = partialMatchesByLeg.get(i);
        for (LegMatchingState match : matches) {
          error
            .append("- Leg with ")
            .append(match.getMatchingCriteria())
            .append(" but missing ")
            .append(match.getMissingCriteria())
            .append("\n");
        }
      }
    }

    throw new AssertionError(error.toString());
  }

  private boolean matchesAllLegs(Itinerary itinerary) {
    List<Leg> remainingLegs = new ArrayList<>(itinerary.legs());

    // Try to find a match for each set of criteria
    for (List<LegCriterion> criteriaSet : legCriteria) {
      boolean foundMatch = false;

      // Look through remaining legs for one that matches this criteria set
      for (int i = 0; i < remainingLegs.size(); i++) {
        Leg leg = remainingLegs.get(i);
        LegMatchingState state = new LegMatchingState(leg);
        criteriaSet.forEach(criterion -> criterion.test().accept(state));

        if (state.isFullMatch()) {
          remainingLegs.remove(i); // Remove the matched leg so it can't be matched again
          foundMatch = true;
          break;
        }
      }

      if (!foundMatch) {
        return false; // Couldn't find a match for this criteria set
      }
    }

    // If strict transit matching is enabled, check that no additional transit legs remain
    if (strictTransitMatching) {
      boolean hasUnmatchedTransitLegs = remainingLegs.stream()
        .anyMatch(Leg::isTransit);
      if (hasUnmatchedTransitLegs) {
        return false; // Found additional transit legs when strict matching is required
      }
    }

    return true;
  }

  private List<LegMatchingState> findPartialMatches(Itinerary itinerary) {
    List<LegMatchingState> partialMatches = new ArrayList<>();
    List<Leg> legs = new ArrayList<>(itinerary.legs());

    for (List<LegCriterion> criteriaSet : legCriteria) {
      for (Leg leg : legs) {
        LegMatchingState state = new LegMatchingState(leg);
        criteriaSet.forEach(criterion -> criterion.test().accept(state));
        if (state.hasAnyMatch()) {
          partialMatches.add(state);
        }
      }
    }

    return partialMatches;
  }

  private String describeCriteria(List<LegCriterion> criteriaSet) {
    StringBuilder message = new StringBuilder();
    for (LegCriterion criterion : criteriaSet) {
      message.append(criterion.message()).append("\n");
    }
    return message.toString();
  }

  private boolean hasAllRequiredLegs(Itinerary itinerary) {
    List<Leg> remainingLegs = new ArrayList<>(itinerary.legs());

    // Try to find a match for each set of criteria (same logic as matchesAllLegs but without strict checking)
    for (List<LegCriterion> criteriaSet : legCriteria) {
      boolean foundMatch = false;

      for (int i = 0; i < remainingLegs.size(); i++) {
        Leg leg = remainingLegs.get(i);
        LegMatchingState state = new LegMatchingState(leg);
        criteriaSet.forEach(criterion -> criterion.test().accept(state));

        if (state.isFullMatch()) {
          remainingLegs.remove(i);
          foundMatch = true;
          break;
        }
      }

      if (!foundMatch) {
        return false;
      }
    }

    return true;
  }

  private List<Leg> findAdditionalTransitLegs(Itinerary itinerary) {
    List<Leg> remainingLegs = new ArrayList<>(itinerary.legs());

    // Remove legs that match our criteria
    for (List<LegCriterion> criteriaSet : legCriteria) {
      for (int i = 0; i < remainingLegs.size(); i++) {
        Leg leg = remainingLegs.get(i);
        LegMatchingState state = new LegMatchingState(leg);
        criteriaSet.forEach(criterion -> criterion.test().accept(state));

        if (state.isFullMatch()) {
          remainingLegs.remove(i);
          break;
        }
      }
    }

    // Return only the remaining transit legs
    return remainingLegs.stream()
      .filter(Leg::isTransit)
      .toList();
  }
}
