package com.arcadis.otp_deployment_tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.opentripplanner.client.model.Itinerary;
import org.opentripplanner.client.model.Leg;
import org.opentripplanner.client.model.TripPlan;

public class SmokeTestItinerary {

    // Each item in this list is a list that represents the complete list of criteria that a leg
    // needs to match in order to pass. For each item, there must be at least one leg that matches all the
    // criteria in the second list.
    private final List<List<LegCriterion>> legCriteria = new ArrayList<>();
    private List<LegCriterion> currentLegCriteria;
    private final TripPlan tripPlan;

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
        currentLegCriteria.add(new LegCriterion(
            message,
            state -> {
                Leg leg = state.getLeg();
                boolean matches =
                    leg.isTransit() &&
                        leg.route().longName().isPresent() &&
                        Arrays.stream(longNames).anyMatch(longName -> Objects.equals(longName, leg.route().longName().get()));
                if (matches) {
                    state.addMatch(message);
                } else {
                    state.addFailure(message);
                }
            }));
        return this;
    }

    public SmokeTestItinerary withRouteShortName(String... shortNames) {
        var message = "route '" + Arrays.toString(shortNames) + "'";
        currentLegCriteria.add(new LegCriterion(
            message,
            state -> {
            Leg leg = state.getLeg();
            boolean matches =
                leg.isTransit() &&
                    leg.route().shortName().isPresent() &&
                    Arrays.stream(shortNames).anyMatch(shortName -> Objects.equals(shortName, leg.route().shortName().get()));
            if (matches) {
                state.addMatch(message);
            } else {
                state.addFailure(message);
            }
        }));
        return this;
    }

    public SmokeTestItinerary withFarePrice(float price, String riderCategoryId, String mediumId) {
        var message= "fare $" + price;
        currentLegCriteria.add(new LegCriterion(
           message,
         state -> {
            Leg leg = state.getLeg();
            boolean matches = leg
                .fareProducts()
                .stream()
                .filter(fp -> fp.product().riderCategory().isPresent())
                .filter(fp -> fp.product().medium().isPresent())
                .filter(fp -> fp.product().riderCategory().get().id().equals(riderCategoryId))
                .filter(fp -> fp.product().medium().get().id().equals(mediumId))
                .anyMatch(fp -> fp.product().price().amount().floatValue() == price);
            if (matches) {
                state.addMatch("fare $" + price);
            } else {
                state.addFailure("fare $" + price);
            }
        }));
        return this;
    }

    public SmokeTestItinerary withMode(String mode) {
        var message = "mode " + mode;
        currentLegCriteria.add(new LegCriterion(message, state -> {
            Leg leg = state.getLeg();
            boolean matches = leg.mode().toString().equals(mode);
            if (matches) {
                state.addMatch("mode " + mode);
            } else {
                state.addFailure("mode " + mode);
            }
        }));
        return this;
    }

    public void assertMatches() {
        List<List<LegMatchingState>> partialMatchesByLeg = new ArrayList<>();

        for (Itinerary itinerary : tripPlan.itineraries()) {
            if (matchesAllLegs(itinerary)) {
                return; // Found an itinerary that matches all leg criteria
            }

            // Store partial matches for error reporting
            List<LegMatchingState> partialMatches = findPartialMatches(itinerary);
            if (!partialMatches.isEmpty()) {
                partialMatchesByLeg.add(partialMatches);
            }
        }

        // If we get here, no itinerary matched all legs
        StringBuilder error = new StringBuilder("No itinerary found matching all required legs:\n");
        for (int i = 0; i < legCriteria.size(); i++) {
            error.append("Leg ").append(i + 1).append(" criteria:\n");
            error.append(describeCriteria(legCriteria.get(i))).append("\n");
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
        for (LegCriterion criterion: criteriaSet) {
            message.append(criterion.message()).append("\n");
        }
        return message.toString();
    }
}
