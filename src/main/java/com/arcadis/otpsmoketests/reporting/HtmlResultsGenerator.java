package com.arcadis.otpsmoketests.reporting;

import static j2html.TagCreator.*;

import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlResultsGenerator {

  private static final Logger logger = LoggerFactory.getLogger(
    HtmlResultsGenerator.class
  );

  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
    .ofPattern("yyyy-MM-dd_HH-mm-ss")
    .withZone(ZoneId.systemDefault());

  private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault());

  public static class TestSuiteReport {

    private final String deploymentName;
    private final String suiteName;
    private final CustomTestRunner.SuiteResult suiteResult;
    private final Instant timestamp;

    public TestSuiteReport(
      String deploymentName,
      String suiteName,
      CustomTestRunner.SuiteResult suiteResult,
      Instant timestamp
    ) {
      this.deploymentName = deploymentName;
      this.suiteName = suiteName;
      this.suiteResult = suiteResult;
      this.timestamp = timestamp;
    }

    public String getDeploymentName() {
      return deploymentName;
    }

    public String getSuiteName() {
      return suiteName;
    }

    public CustomTestRunner.SuiteResult getSuiteResult() {
      return suiteResult;
    }

    public Instant getTimestamp() {
      return timestamp;
    }
  }

  public static Path generateHtmlReport(TestSuiteReport report) {
    try {
      // Create directory structure: ./results/[deploymentName]/[suiteName]/
      Path resultsDir = Paths
        .get("results", report.getDeploymentName(), report.getSuiteName())
        .toAbsolutePath();
      Files.createDirectories(resultsDir);

      // Generate filename with timestamp
      String timestamp = TIMESTAMP_FORMATTER.format(report.getTimestamp());
      String filename = timestamp + ".html";
      Path htmlFile = resultsDir.resolve(filename);

      // Generate HTML content
      String htmlContent = generateHtmlContent(report);

      // Write to file
      Files.writeString(htmlFile, htmlContent);

      logger.info(
        "Generated HTML test report: {}",
        htmlFile.toAbsolutePath().toString()
      );

      return htmlFile;
    } catch (IOException e) {
      logger.error("Failed to generate HTML report", e);
      throw new RuntimeException("Failed to generate HTML report", e);
    }
  }

  private static String generateHtmlContent(TestSuiteReport report) {
    CustomTestRunner.SuiteResult result = report.getSuiteResult();
    List<CustomTestRunner.TestResult> testResults = result.testResults();

    // Split tests into passed and failed
    List<CustomTestRunner.TestResult> passedTests = testResults
      .stream()
      .filter(CustomTestRunner.TestResult::isPassed)
      .toList();

    List<CustomTestRunner.TestResult> failedTests = testResults
      .stream()
      .filter(test -> !test.isPassed())
      .toList();

    String pageTitle = String.format(
      "Test Results: %s - %s",
      report.getDeploymentName(),
      report.getSuiteName()
    );

    HtmlTag htmlContent = html(
      head(
        title(pageTitle),
        meta().withCharset("UTF-8"),
        meta()
          .withName("viewport")
          .withContent("width=device-width, initial-scale=1.0"),
        style(getCssStyles())
      ),
      body(
        div(
          attrs(".container"),
          // Header
          div(
            attrs(".header"),
            h1("OTP Test Results"),
            div(
              attrs(".test-info"),
              p(strong("Deployment: "), text(report.getDeploymentName())),
              p(strong("Test Suite: "), text(report.getSuiteName())),
              p(
                strong("Execution Time: "),
                text(DISPLAY_FORMATTER.format(report.getTimestamp()))
              ),
              p(strong("Duration: "), text(result.totalDurationMs() + " ms"))
            )
          ),
          // Summary
          div(
            attrs(".summary"),
            h2("Summary"),
            div(
              attrs(".summary-grid"),
              div(
                attrs(".summary-card.total"),
                div(
                  attrs(".summary-number"),
                  text(String.valueOf(testResults.size()))
                ),
                div(attrs(".summary-label"), text("Total Tests"))
              ),
              div(
                attrs(".summary-card.passed"),
                div(
                  attrs(".summary-number"),
                  text(String.valueOf(passedTests.size()))
                ),
                div(attrs(".summary-label"), text("Passed"))
              ),
              div(
                attrs(".summary-card.failed"),
                div(
                  attrs(".summary-number"),
                  text(String.valueOf(failedTests.size()))
                ),
                div(attrs(".summary-label"), text("Failed"))
              )
            )
          ),
          // Failed Tests Section
          failedTests.isEmpty()
            ? div()
            : div(
              attrs(".section"),
              h2("Failed Tests (" + failedTests.size() + ")"),
              div(
                attrs(".tests-container"),
                each(failedTests, test -> generateFailedTestCard(test))
              )
            ),
          // Passed Tests Section
          passedTests.isEmpty()
            ? div()
            : div(
              attrs(".section"),
              h2("Passed Tests (" + passedTests.size() + ")"),
              div(
                attrs(".tests-container"),
                each(passedTests, test -> generatePassedTestCard(test))
              )
            )
        )
      )
    );

    return htmlContent.render();
  }

  private static DivTag generateFailedTestCard(
    CustomTestRunner.TestResult test
  ) {
    return div(
      attrs(".test-card.failed"),
      div(
        attrs(".test-header"),
        h3(test.getTestName()),
        span(attrs(".test-duration"), text(test.getDurationMs() + " ms"))
      ),
      div(
        attrs(".test-content"),
        test.getException() != null
          ? generateStructuredFailureContent(test.getException())
          : div(attrs(".error-message"), text("Test failed with unknown error"))
      )
    );
  }

  private static DivTag generateStructuredFailureContent(Throwable exception) {
    String errorMessage = getErrorMessage(exception);
    TestFailureParser.ParsedTestFailure parsedFailure = TestFailureParser.parseFailure(errorMessage);

    if (!parsedFailure.isStructured()) {
      // Fall back to original display for non-structured errors
      return div(
        div(
          attrs(".error-message"),
          strong("Error: "),
          text(errorMessage)
        ),
        div(
          attrs(".stack-trace"),
          strong("Stack Trace:"),
          pre(text(getFormattedStackTrace(exception)))
        )
      );
    }

    return div(
      // Main error summary
      div(
        attrs(".failure-summary"),
        h4("Test Failure Summary"),
        p(attrs(".main-error"), text(parsedFailure.getMainError()))
      ),

      // Expected criteria section
      !parsedFailure.getExpectedCriteria().isEmpty() ? div(
        attrs(".expected-section"),
        h4("Expected Criteria"),
        div(
          attrs(".criteria-list"),
          each(parsedFailure.getExpectedCriteria(), criteria ->
            div(
              attrs(".criteria-item"),
              span(attrs(".leg-number"), text("Leg " + criteria.getLegNumber() + ":")),
              span(attrs(".criteria-text"), text(criteria.getCriteria()))
            )
          )
        )
      ) : div(),

      // Itinerary failures section
      !parsedFailure.getItineraryFailures().isEmpty() ? div(
        attrs(".itinerary-failures-section"),
        h4("Itinerary Analysis"),
        div(
          attrs(".itinerary-failures-grid"),
          each(parsedFailure.getItineraryFailures(), failure ->
            div(
              attrs(".itinerary-failure"),
              div(
                attrs(".itinerary-header"),
                strong("Itinerary " + failure.getItineraryNumber())
              ),
              div(
                attrs(".missing-criteria"),
                failure.getMissingCriteria().isEmpty() 
                  ? span(attrs(".success-badge"), "✓ All criteria met")
                  : each(failure.getMissingCriteria(), missing ->
                      div(attrs(".missing-item"), "✗ " + missing)
                    )
              )
            )
          )
        )
      ) : div(),

      // Partial matches section
      !parsedFailure.getPartialMatches().isEmpty() ? div(
        attrs(".partial-matches-section"),
        h4("Partial Matches"),
        div(
          attrs(".partial-matches-list"),
          each(parsedFailure.getPartialMatches(), match ->
            div(
              attrs(".partial-match"),
              div(
                attrs(".match-found"),
                span(attrs(".found-badge"), "✓ Found: "),
                text(match.getFoundCriteria())
              ),
              div(
                attrs(".match-missing"),
                span(attrs(".missing-badge"), "✗ Missing: "),
                text(match.getMissingCriteria())
              )
            )
          )
        )
      ) : div(),

      // Collapsible stack trace
      div(
        attrs(".stack-trace-section"),
        details(
          summary("Technical Details"),
          div(
            attrs(".stack-trace"),
            pre(text(getFormattedStackTrace(exception)))
          )
        )
      )
    );
  }

  private static DivTag generatePassedTestCard(
    CustomTestRunner.TestResult test
  ) {
    return div(
      attrs(".test-card.passed"),
      div(
        attrs(".test-header"),
        h3(test.getTestName()),
        span(attrs(".test-duration"), text(test.getDurationMs() + " ms"))
      )
    );
  }

  private static String getErrorMessage(Throwable throwable) {
    String message = throwable.getMessage();
    return message != null ? message : throwable.getClass().getSimpleName();
  }

  private static String getFormattedStackTrace(Throwable throwable) {
    return Arrays
      .stream(throwable.getStackTrace())
      .map(StackTraceElement::toString)
      .limit(10) // Limit to first 10 lines to keep HTML readable
      .reduce("", (acc, line) -> acc + line + "\n");
  }

  private static String getCssStyles() {
    return """
      body {
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        line-height: 1.6;
        margin: 0;
        padding: 0;
        background-color: #f5f5f5;
        color: #333;
      }
      
      .container {
        max-width: 1200px;
        margin: 0 auto;
        padding: 20px;
      }
      
      .header {
        background: white;
        padding: 30px;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        margin-bottom: 30px;
      }
      
      .header h1 {
        margin: 0 0 20px 0;
        color: #2c3e50;
        font-size: 2.5em;
      }
      
      .test-info p {
        margin: 8px 0;
        font-size: 1.1em;
      }
      
      .summary {
        background: white;
        padding: 30px;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        margin-bottom: 30px;
      }
      
      .summary h2 {
        margin: 0 0 20px 0;
        color: #2c3e50;
      }
      
      .summary-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 20px;
      }
      
      .summary-card {
        padding: 20px;
        border-radius: 8px;
        text-align: center;
        color: white;
      }
      
      .summary-card.total {
        background-color: #3498db;
      }
      
      .summary-card.passed {
        background-color: #27ae60;
      }
      
      .summary-card.failed {
        background-color: #e74c3c;
      }
      
      .summary-number {
        font-size: 2.5em;
        font-weight: bold;
        margin-bottom: 8px;
      }
      
      .summary-label {
        font-size: 1.1em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      
      .section {
        background: white;
        padding: 30px;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        margin-bottom: 30px;
      }
      
      .section h2 {
        margin: 0 0 20px 0;
        color: #2c3e50;
      }
      
      .tests-container {
        display: grid;
        gap: 15px;
      }
      
      .test-card {
        border-radius: 6px;
        padding: 20px;
        border-left: 4px solid;
      }
      
      .test-card.passed {
        background-color: #d5f4e6;
        border-left-color: #27ae60;
      }
      
      .test-card.failed {
        background-color: #fadbd8;
        border-left-color: #e74c3c;
      }
      
      .test-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
      }
      
      .test-header h3 {
        margin: 0;
        color: #2c3e50;
        font-size: 1.2em;
      }
      
      .test-duration {
        color: #7f8c8d;
        font-size: 0.9em;
        font-weight: 500;
      }
      
      .test-content {
        color: #2c3e50;
      }
      
      .error-message {
        margin-bottom: 15px;
        font-size: 1em;
        color: #c0392b;
      }
      
      .stack-trace {
        margin-top: 15px;
      }
      
      .stack-trace pre {
        background-color: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 4px;
        padding: 15px;
        overflow-x: auto;
        font-size: 0.9em;
        color: #495057;
        margin: 10px 0 0 0;
      }

      /* Enhanced structured failure styles */
      .failure-summary {
        background-color: #fff3cd;
        border: 1px solid #ffeaa7;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 20px;
      }

      .failure-summary h4 {
        margin: 0 0 10px 0;
        color: #856404;
        font-size: 1.1em;
      }

      .main-error {
        margin: 0;
        font-weight: 500;
        color: #d63031;
      }

      .expected-section {
        background-color: #e8f4fd;
        border: 1px solid #74b9ff;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 20px;
      }

      .expected-section h4 {
        margin: 0 0 15px 0;
        color: #0984e3;
        font-size: 1.1em;
      }

      .criteria-list {
        display: grid;
        gap: 10px;
      }

      .criteria-item {
        background-color: white;
        padding: 10px;
        border-radius: 4px;
        border-left: 3px solid #0984e3;
      }

      .leg-number {
        font-weight: bold;
        color: #0984e3;
        margin-right: 10px;
      }

      .criteria-text {
        color: #2c3e50;
      }

      .itinerary-failures-section {
        background-color: #ffeef0;
        border: 1px solid #ff7675;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 20px;
      }

      .itinerary-failures-section h4 {
        margin: 0 0 15px 0;
        color: #d63031;
        font-size: 1.1em;
      }

      .itinerary-failures-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 15px;
      }

      .itinerary-failure {
        background-color: white;
        border-radius: 4px;
        padding: 12px;
        border: 1px solid #ddd;
      }

      .itinerary-header {
        margin-bottom: 10px;
        padding-bottom: 8px;
        border-bottom: 1px solid #eee;
        color: #2c3e50;
      }

      .missing-criteria {
        display: grid;
        gap: 5px;
      }

      .missing-item {
        color: #d63031;
        font-size: 0.9em;
        padding: 2px 0;
      }

      .success-badge {
        color: #00b894;
        font-weight: 500;
      }

      .partial-matches-section {
        background-color: #fff8e1;
        border: 1px solid #ffcc02;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 20px;
      }

      .partial-matches-section h4 {
        margin: 0 0 15px 0;
        color: #f39c12;
        font-size: 1.1em;
      }

      .partial-matches-list {
        display: grid;
        gap: 12px;
      }

      .partial-match {
        background-color: white;
        border-radius: 4px;
        padding: 12px;
        border-left: 3px solid #f39c12;
      }

      .match-found {
        margin-bottom: 5px;
        font-size: 0.9em;
      }

      .match-missing {
        font-size: 0.9em;
      }

      .found-badge {
        color: #00b894;
        font-weight: bold;
      }

      .missing-badge {
        color: #d63031;
        font-weight: bold;
      }

      .stack-trace-section {
        margin-top: 20px;
      }

      .stack-trace-section details {
        border: 1px solid #ddd;
        border-radius: 4px;
        padding: 0;
      }

      .stack-trace-section summary {
        background-color: #f8f9fa;
        padding: 10px 15px;
        cursor: pointer;
        user-select: none;
        font-weight: 500;
        color: #6c757d;
        border-radius: 4px 4px 0 0;
      }

      .stack-trace-section summary:hover {
        background-color: #e9ecef;
      }

      .stack-trace-section .stack-trace {
        margin: 0;
        border-top: 1px solid #ddd;
      }

      .stack-trace-section .stack-trace pre {
        margin: 0;
        border-radius: 0 0 4px 4px;
        border: none;
      }

      @media (max-width: 768px) {
        .container {
          padding: 10px;
        }

        .header, .summary, .section {
          padding: 20px;
        }

        .header h1 {
          font-size: 2em;
        }

        .summary-grid {
          grid-template-columns: 1fr;
        }

        .test-header {
          flex-direction: column;
          align-items: flex-start;
          gap: 10px;
        }

        .itinerary-failures-grid {
          grid-template-columns: 1fr;
        }
      }
      """;
  }
}
