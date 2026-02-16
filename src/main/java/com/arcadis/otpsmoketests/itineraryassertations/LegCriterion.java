package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.function.Consumer;

/***
 * Used to store one criteria to be applied to a leg.
 * @param message Message describing this criteria
 * @param test A consumer that applies the checks and adds the status to the LegMatchingState.
 */
public record LegCriterion(String message, Consumer<LegMatchingState> test) {}
