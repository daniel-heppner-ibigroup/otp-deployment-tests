package com.arcadis.otp_deployment_tests;

import java.util.function.Consumer;

public record LegCriterion(String message, Consumer<LegMatchingState> test) { }
