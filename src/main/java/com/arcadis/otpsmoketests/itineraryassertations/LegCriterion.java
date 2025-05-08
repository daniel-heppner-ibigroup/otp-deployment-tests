package com.arcadis.otpsmoketests.itineraryassertations;

import java.util.function.Consumer;

public record LegCriterion(String message, Consumer<LegMatchingState> test) {}
