package com.arcadis.otp_deployment_tests;

public class MetricNames {
    private static final String PLAN_REQUEST_BASE = "otp.plan.requests";
    
    public static String planRequestTimer(String suiteName, String testName) {
        return String.format("%s.%s.%s.duration", PLAN_REQUEST_BASE, suiteName, testName);
    }

    public static String planRequestTimer(String suiteName) {
        return String.format("%s.%s.duration", PLAN_REQUEST_BASE, suiteName);
    }

    public static String planRequestDescription(String suiteName, String testName) {
        return String.format("Time taken for OTP to respond to plan requests in %s.%s", suiteName, testName);
    }
} 