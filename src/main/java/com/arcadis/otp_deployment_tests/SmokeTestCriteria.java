package com.arcadis.otp_deployment_tests;

import java.util.function.Consumer;

public record SmokeTestCriteria(String message, Consumer<LegMatchingState> test) { }
