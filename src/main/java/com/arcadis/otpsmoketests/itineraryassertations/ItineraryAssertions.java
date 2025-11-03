package com.arcadis.otpsmoketests.itineraryassertations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
public class ItineraryAssertions {

  // Each item in this list is a list that represents the complete list of criteria that a leg
  // needs to match in order to pass. For each item, there must be at least one leg that matches all the
  // criteria in the second list.
  private final List<List<LegCriterion>> distinctLegCriteria = new ArrayList<>();
  private List<LegCriterion> currentLegCriteria;
  private boolean strictTransitMatching = false;

  public ItineraryAssertions hasLeg() {
    currentLegCriteria = new ArrayList<>();
    distinctLegCriteria.add(currentLegCriteria);
    return this;
  }

  public ItineraryAssertions withRouteLongName(String... longNames) {
    var message = "route '%s'".formatted(Arrays.toString(longNames));
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

  public ItineraryAssertions withMaxDuration(Duration duration) {
    var message = "duration '%s'".formatted(duration);
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches =
            leg.isTransit() && leg.duration().compareTo(duration) < 1;
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

  public ItineraryAssertions withRouteShortName(String... shortNames) {
    var message = "route '%s'".formatted(Arrays.toString(shortNames));
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

  public ItineraryAssertions withFarePrice(
    float price,
    String riderCategoryId,
    String mediumId
  ) {
    var message = "fare $%.2f".formatted(price);
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
            state.addMatch(message);
          } else {
            state.addFailure(message);
          }
        }
      )
    );
    return this;
  }

  public ItineraryAssertions interlinedWithPreviousLeg() {
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

  public ItineraryAssertions withMode(String mode) {
    var message = "mode %s".formatted(mode);
    currentLegCriteria.add(
      new LegCriterion(
        message,
        state -> {
          Leg leg = state.getLeg();
          boolean matches = leg.mode().toString().equals(mode);
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

  public ItineraryAssertions withStrictTransitMatching() {
    this.strictTransitMatching = true;
    return this;
  }

  public void assertMatches(TripPlan tripPlan) {
    List<LegMatchResult> failedResults = new ArrayList<>();

    for (Itinerary itinerary : tripPlan.itineraries()) {
      LegMatchResult result = matchesAllLegs(itinerary);
      if (result.isSuccess()) {
        return;
      }
      failedResults.add(result);
    }

    // If we get here, no itinerary matched all legs
    String strictMatchingText = strictTransitMatching
      ? " with strict transit matching"
      : "";
    String header =
      "No itinerary found matching all required legs%s:%n".formatted(
          strictMatchingText
        );

    StringBuilder criteriaSection = new StringBuilder();
    for (int i = 0; i < distinctLegCriteria.size(); i++) {
      criteriaSection.append(
        "Leg %d criteria:%n%s%n".formatted(
            i + 1,
            describeCriteria(distinctLegCriteria.get(i))
          )
      );
    }

    StringBuilder failuresSection = new StringBuilder();
    failuresSection.append("%nFailures by itinerary:%n");

    for (int i = 0; i < failedResults.size(); i++) {
      LegMatchResult result = failedResults.get(i);
      Itinerary itinerary = tripPlan.itineraries().get(i);
      failuresSection.append("Itinerary %d:%n".formatted(i + 1));

      // Add errors
      result
        .errors()
        .forEach(err -> failuresSection.append("  - %s%n".formatted(err)));

      // Add partial matches
      if (!result.getPartialMatches().isEmpty()) {
        failuresSection.append("  Partial matches:%n");
        result
          .getPartialMatches()
          .forEach(match ->
            failuresSection.append(
              "    - Leg with %s but missing %s%n".formatted(
                  match.getMatchingCriteria(),
                  match.getMissingCriteria()
                )
            )
          );
      }

      // Add actual itinerary breakdown
      failuresSection.append("  Actual itinerary:%n");
      for (int legIndex = 0; legIndex < itinerary.legs().size(); legIndex++) {
        Leg leg = itinerary.legs().get(legIndex);
        failuresSection.append(
          "    Leg %d: %s".formatted(legIndex + 1, formatLegDescription(leg))
        );
      }
      failuresSection.append("%n");
    }

    String fullError = header + criteriaSection + failuresSection;
    throw new ItineraryAssertationError(fullError, failedResults);
  }

  /**
   * Check each requirement to make sure that some leg on the itinerary matches
   * If strictItineraryMatching is true, then all transit legs must match a requirement.
   * @param itinerary Itinerary to be checked
   * @return MatchResult object containing the results
   */
  private LegMatchResult matchesAllLegs(Itinerary itinerary) {
    List<Leg> remainingLegs = new ArrayList<>(itinerary.legs());
    List<String> errors = new ArrayList<>();
    List<LegMatchingState> completeMatches = new ArrayList<>();
    List<LegMatchingState> partialMatches = new ArrayList<>();

    if (distinctLegCriteria.isEmpty()) {
      throw new IllegalArgumentException("No leg criteria specified");
    }

    // Try to find a match for each set of criteria
    for (
      var criteriaIndex = 0;
      criteriaIndex < distinctLegCriteria.size();
      criteriaIndex++
    ) {
      List<LegCriterion> criteriaSet = distinctLegCriteria.get(criteriaIndex);
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
          completeMatches.add(state);
          break;
        } else if (state.hasAnyMatch()) {
          // Keep track of partial matches for error reporting
          partialMatches.add(state);
        }
      }

      if (!foundMatch) {
        errors.add(
          "No leg found matching criteria set %d: %s".formatted(
              criteriaIndex + 1,
              describeCriteria(criteriaSet).trim()
            )
        );
      }
    }

    List<Leg> extraMatches = new ArrayList<>();
    // If strict transit matching is enabled, check that no additional transit legs remain
    if (strictTransitMatching && errors.isEmpty()) {
      List<Leg> additionalTransitLegs = remainingLegs
        .stream()
        .filter(Leg::isTransit)
        .toList();

      if (!additionalTransitLegs.isEmpty()) {
        extraMatches.addAll(additionalTransitLegs);
        String extraLegNames = additionalTransitLegs
          .stream()
          .map(leg ->
            leg
              .route()
              .shortName()
              .or(() -> leg.route().longName())
              .orElse(leg.mode().toString())
          )
          .collect(Collectors.joining(" "));

        errors.add(
          "Itinerary contains additional transit legs when strict matching is enabled: %s".formatted(
              extraLegNames
            )
        );
      }
    }

    if (errors.isEmpty()) {
      return LegMatchResult.success();
    } else {
      return new LegMatchResult(
        completeMatches,
        partialMatches,
        extraMatches,
        errors
      );
    }
  }

  private String formatLegDescription(Leg leg) {
    if (leg.isTransit()) {
      String routeName = leg
        .route()
        .shortName()
        .or(() -> leg.route().longName())
        .orElse(leg.mode().toString());

      String interlinedText = leg.interlineWithPreviousLeg()
        ? " (interlined)"
        : "";
      return "TRANSIT - Route: %s%s, From: %s, To: %s%n".formatted(
          routeName,
          interlinedText,
          leg.from().name(),
          leg.to().name()
        );
    } else {
      return "%s - From: %s, To: %s, Distance: %.0fm%n".formatted(
          leg.mode().toString().toUpperCase(),
          leg.from().name(),
          leg.to().name(),
          leg.distance()
        );
    }
  }

  private String describeCriteria(List<LegCriterion> criteriaSet) {
    StringBuilder message = new StringBuilder();
    for (LegCriterion criterion : criteriaSet) {
      message.append(criterion.message()).append("\n");
    }
    return message.toString();
  }
}
