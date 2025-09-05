package com.arcadis.otpsmoketests.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFailureParser {

  public static class ParsedTestFailure {

    private final String originalMessage;
    private final String mainError;
    private final List<LegCriteria> expectedCriteria;
    private final List<ItineraryFailure> itineraryFailures;
    private final List<PartialMatch> partialMatches;
    private final boolean isStructured;

    public ParsedTestFailure(
      String originalMessage,
      String mainError,
      List<LegCriteria> expectedCriteria,
      List<ItineraryFailure> itineraryFailures,
      List<PartialMatch> partialMatches,
      boolean isStructured
    ) {
      this.originalMessage = originalMessage;
      this.mainError = mainError;
      this.expectedCriteria = expectedCriteria != null ? expectedCriteria : new ArrayList<>();
      this.itineraryFailures = itineraryFailures != null ? itineraryFailures : new ArrayList<>();
      this.partialMatches = partialMatches != null ? partialMatches : new ArrayList<>();
      this.isStructured = isStructured;
    }

    public String getOriginalMessage() {
      return originalMessage;
    }

    public String getMainError() {
      return mainError;
    }

    public List<LegCriteria> getExpectedCriteria() {
      return expectedCriteria;
    }

    public List<ItineraryFailure> getItineraryFailures() {
      return itineraryFailures;
    }

    public List<PartialMatch> getPartialMatches() {
      return partialMatches;
    }

    public boolean isStructured() {
      return isStructured;
    }
  }

  public static class LegCriteria {

    private final int legNumber;
    private final String criteria;

    public LegCriteria(int legNumber, String criteria) {
      this.legNumber = legNumber;
      this.criteria = criteria;
    }

    public int getLegNumber() {
      return legNumber;
    }

    public String getCriteria() {
      return criteria;
    }
  }

  public static class ItineraryFailure {

    private final int itineraryNumber;
    private final List<String> missingCriteria;

    public ItineraryFailure(int itineraryNumber, List<String> missingCriteria) {
      this.itineraryNumber = itineraryNumber;
      this.missingCriteria = missingCriteria != null ? missingCriteria : new ArrayList<>();
    }

    public int getItineraryNumber() {
      return itineraryNumber;
    }

    public List<String> getMissingCriteria() {
      return missingCriteria;
    }
  }

  public static class PartialMatch {

    private final String foundCriteria;
    private final String missingCriteria;

    public PartialMatch(String foundCriteria, String missingCriteria) {
      this.foundCriteria = foundCriteria;
      this.missingCriteria = missingCriteria;
    }

    public String getFoundCriteria() {
      return foundCriteria;
    }

    public String getMissingCriteria() {
      return missingCriteria;
    }
  }

  private static final Pattern MAIN_ERROR_PATTERN = Pattern.compile(
    "^(.+?)(?=:)"
  );
  private static final Pattern LEG_CRITERIA_PATTERN = Pattern.compile(
    "Leg (\\d+) criteria:\\s*(.+?)(?=\\n|$)"
  );
  private static final Pattern ITINERARY_FAILURE_PATTERN = Pattern.compile(
    "Itinerary (\\d+):\\s*(.+?)(?=(?:Itinerary \\d+:|Partial matches:|$))",
    Pattern.DOTALL
  );
  private static final Pattern FAILURE_LINE_PATTERN = Pattern.compile(
    "- (.+?)(?=\\n|$)"
  );
  private static final Pattern PARTIAL_MATCH_PATTERN = Pattern.compile(
    "- Leg with (.+?) but missing (.+?)(?=\\n|$)"
  );

  public static ParsedTestFailure parseFailure(String errorMessage) {
    if (errorMessage == null || errorMessage.trim().isEmpty()) {
      return new ParsedTestFailure(errorMessage, errorMessage, null, null, null, false);
    }

    // Check if this looks like a SmokeTestItinerary error
    if (!errorMessage.contains("itinerary") || !errorMessage.contains("criteria")) {
      return new ParsedTestFailure(errorMessage, errorMessage, null, null, null, false);
    }

    try {
      // Extract main error
      String mainError = extractMainError(errorMessage);

      // Extract expected criteria
      List<LegCriteria> expectedCriteria = extractLegCriteria(errorMessage);

      // Extract itinerary failures
      List<ItineraryFailure> itineraryFailures = extractItineraryFailures(errorMessage);

      // Extract partial matches
      List<PartialMatch> partialMatches = extractPartialMatches(errorMessage);

      return new ParsedTestFailure(
        errorMessage,
        mainError,
        expectedCriteria,
        itineraryFailures,
        partialMatches,
        true
      );
    } catch (Exception e) {
      // If parsing fails, fall back to original message
      return new ParsedTestFailure(errorMessage, errorMessage, null, null, null, false);
    }
  }

  private static String extractMainError(String errorMessage) {
    Matcher matcher = MAIN_ERROR_PATTERN.matcher(errorMessage);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    // Fallback: take first line
    String[] lines = errorMessage.split("\\n");
    return lines.length > 0 ? lines[0].trim() : errorMessage;
  }

  private static List<LegCriteria> extractLegCriteria(String errorMessage) {
    List<LegCriteria> criteria = new ArrayList<>();
    Matcher matcher = LEG_CRITERIA_PATTERN.matcher(errorMessage);

    while (matcher.find()) {
      int legNumber = Integer.parseInt(matcher.group(1));
      String criteriaText = matcher.group(2).trim();
      criteria.add(new LegCriteria(legNumber, criteriaText));
    }

    return criteria;
  }

  private static List<ItineraryFailure> extractItineraryFailures(String errorMessage) {
    List<ItineraryFailure> failures = new ArrayList<>();

    // Find the "Failures by itinerary:" section
    int failuresStart = errorMessage.indexOf("Failures by itinerary:");
    if (failuresStart == -1) return failures;

    int partialMatchesStart = errorMessage.indexOf("Partial matches:");
    String failuresSection = partialMatchesStart != -1
      ? errorMessage.substring(failuresStart, partialMatchesStart)
      : errorMessage.substring(failuresStart);

    Matcher matcher = ITINERARY_FAILURE_PATTERN.matcher(failuresSection);

    while (matcher.find()) {
      int itineraryNumber = Integer.parseInt(matcher.group(1));
      String failureText = matcher.group(2).trim();

      List<String> missingCriteria = new ArrayList<>();
      Matcher failureMatcher = FAILURE_LINE_PATTERN.matcher(failureText);

      while (failureMatcher.find()) {
        missingCriteria.add(failureMatcher.group(1).trim());
      }

      failures.add(new ItineraryFailure(itineraryNumber, missingCriteria));
    }

    return failures;
  }

  private static List<PartialMatch> extractPartialMatches(String errorMessage) {
    List<PartialMatch> partialMatches = new ArrayList<>();

    // Find the "Partial matches:" section
    int partialStart = errorMessage.indexOf("Partial matches:");
    if (partialStart == -1) return partialMatches;

    String partialSection = errorMessage.substring(partialStart);
    Matcher matcher = PARTIAL_MATCH_PATTERN.matcher(partialSection);

    while (matcher.find()) {
      String foundCriteria = matcher.group(1).trim();
      String missingCriteria = matcher.group(2).trim();
      partialMatches.add(new PartialMatch(foundCriteria, missingCriteria));
    }

    return partialMatches;
  }
}