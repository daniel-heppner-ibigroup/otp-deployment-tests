package com.arcadis.otpsmoketests.reporting;

import static j2html.TagCreator.*;

import com.arcadis.otpsmoketests.itineraryassertations.ItineraryAssertationError;
import com.arcadis.otpsmoketests.itineraryassertations.LegMatchingState;
import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestItinerary;
import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import j2html.tags.ContainerTag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlReportGenerator {

  private static final Logger logger = LoggerFactory.getLogger(
    HtmlReportGenerator.class
  );
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd_HH-mm-ss"
  );

  public void generateReport(
    CustomTestRunner.SuiteResult suiteResult,
    String deploymentName,
    String baseUrl
  ) {
    try {
      String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
      Path reportPath = createReportPath(
        deploymentName,
        suiteResult.suiteName(),
        timestamp
      );

      String htmlContent = generateHtmlContent(suiteResult, baseUrl, timestamp);
      Files.writeString(reportPath, htmlContent);

      logger.info(
        "Generated HTML report for suite '{}' at: {}",
        suiteResult.suiteName(),
        reportPath
      );
    } catch (IOException e) {
      logger.error(
        "Failed to generate HTML report for suite '{}'",
        suiteResult.suiteName(),
        e
      );
    }
  }

  private Path createReportPath(
    String deploymentName,
    String suiteName,
    String timestamp
  ) throws IOException {
    String safeDeploymentName = deploymentName != null
      ? sanitizeFileName(deploymentName)
      : "unknown";
    String safeSuiteName = sanitizeFileName(suiteName);

    Path resultsDir = Paths.get("results", safeDeploymentName, safeSuiteName);
    Files.createDirectories(resultsDir);

    return resultsDir.resolve(timestamp + ".html");
  }

  private String sanitizeFileName(String fileName) {
    return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private String generateHtmlContent(
    CustomTestRunner.SuiteResult suiteResult,
    String baseUrl,
    String timestamp
  ) {
    return html(
      head(
        title(
          "Test Report: " +
          suiteResult.suiteName() +
          " - " +
          timestamp.replace("_", " ")
        ),
        style(getCssStyles())
      ),
      body(
        div(
          attrs(".container"),
          generateHeader(suiteResult, baseUrl, timestamp),
          generateSummary(suiteResult),
          generateTestResults(suiteResult.testResults())
        )
      )
    )
      .render();
  }

  private ContainerTag generateHeader(
    CustomTestRunner.SuiteResult suiteResult,
    String baseUrl,
    String timestamp
  ) {
    return header(
      attrs(".header"),
      h1("OTP Smoke Test Report"),
      div(
        attrs(".test-info"),
        p(strong("Test Suite: "), text(suiteResult.suiteName())),
        p(strong("Base URL: "), text(baseUrl)),
        p(
          strong("Generated: "),
          text(timestamp.replace("_", " ").replace("-", ":"))
        ),
        p(strong("Duration: "), text(suiteResult.totalDurationMs() + "ms"))
      )
    );
  }

  private ContainerTag generateSummary(
    CustomTestRunner.SuiteResult suiteResult
  ) {
    String summaryClass = suiteResult.getTestsFailedCount() > 0
      ? "summary failed"
      : "summary passed";

    return section(
      attrs("." + summaryClass),
      h2("Test Summary"),
      div(
        attrs(".summary-grid"),
        div(
          attrs(".summary-item"),
          span(
            attrs(".summary-number"),
            text(String.valueOf(suiteResult.getTestsFoundCount()))
          ),
          span(attrs(".summary-label"), text("Total Tests"))
        ),
        div(
          attrs(".summary-item passed"),
          span(
            attrs(".summary-number"),
            text(String.valueOf(suiteResult.getTestsSucceededCount()))
          ),
          span(attrs(".summary-label"), text("Passed"))
        ),
        div(
          attrs(".summary-item failed"),
          span(
            attrs(".summary-number"),
            text(String.valueOf(suiteResult.getTestsFailedCount()))
          ),
          span(attrs(".summary-label"), text("Failed"))
        )
      )
    );
  }

  private ContainerTag generateTestResults(
    List<CustomTestRunner.TestResult> testResults
  ) {
    return section(
      attrs(".test-results"),
      h2("Test Results"),
      div(
        attrs(".tests"),
        testResults
          .stream()
          .map(this::generateTestResult)
          .toArray(ContainerTag[]::new)
      )
    );
  }

  private ContainerTag generateTestResult(
    CustomTestRunner.TestResult testResult
  ) {
    String testClass = testResult.isPassed()
      ? "test-item passed"
      : "test-item failed";

    ContainerTag testDiv = div(
      attrs("." + testClass),
      div(
        attrs(".test-header"),
        h3(testResult.getTestName()),
        span(
          attrs(".test-status"),
          text(testResult.isPassed() ? "PASSED" : "FAILED")
        ),
        span(attrs(".test-duration"), text(testResult.getDurationMs() + "ms"))
      )
    );

    if (!testResult.isPassed() && testResult.getException() != null) {
      if (testResult.getException() instanceof ItineraryAssertationError) {
        ItineraryAssertationError error = (ItineraryAssertationError) testResult.getException();
        testDiv.with(generateItineraryErrorDetails(error));
      } else {
        testDiv.with(
          div(
            attrs(".error-details"),
            h4("Error Details"),
            pre(
              attrs(".error-message"),
              text(testResult.getException().getMessage())
            )
          )
        );
      }
    }

    return testDiv;
  }

  private ContainerTag generateItineraryErrorDetails(
    ItineraryAssertationError error
  ) {
    ContainerTag errorDiv = div(
      attrs(".error-details"),
      h4("Itinerary Assertion Failures"),
      pre(attrs(".error-message"), text(error.getMessage()))
    );

    if (
      error.getFailedResults() != null && !error.getFailedResults().isEmpty()
    ) {
      errorDiv.with(
        div(
          attrs(".failed-results"),
          h5("Detailed Match Results"),
          div(
            attrs(".match-results"),
            error
              .getFailedResults()
              .stream()
              .map(this::generateMatchResult)
              .toArray(ContainerTag[]::new)
          )
        )
      );
    }

    return errorDiv;
  }

  private ContainerTag generateMatchResult(
    SmokeTestItinerary.MatchResult matchResult
  ) {
    ContainerTag resultDiv = div(attrs(".match-result"));

    if (!matchResult.getErrors().isEmpty()) {
      resultDiv.with(
        div(
          attrs(".match-errors"),
          h6("Errors:"),
          ul(
            matchResult
              .getErrors()
              .stream()
              .map(error -> li(text(error)))
              .toArray(ContainerTag[]::new)
          )
        )
      );
    }

    if (!matchResult.getPartialMatches().isEmpty()) {
      resultDiv.with(
        div(
          attrs(".partial-matches"),
          h6("Partial Matches:"),
          ul(
            matchResult
              .getPartialMatches()
              .stream()
              .map(this::generatePartialMatch)
              .toArray(ContainerTag[]::new)
          )
        )
      );
    }

    return resultDiv;
  }

  private ContainerTag generatePartialMatch(LegMatchingState partialMatch) {
    return li(
      "Matched: " +
      partialMatch.getMatchingCriteria() +
      " | Missing: " +
      partialMatch.getMissingCriteria()
    );
  }

  private String getCssStyles() {
    return """
      body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        line-height: 1.6;
        color: #333;
        background-color: #f5f5f5;
        margin: 0;
        padding: 20px;
      }
      
      .container {
        max-width: 1200px;
        margin: 0 auto;
        background: white;
        border-radius: 8px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        overflow: hidden;
      }
      
      .header {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        padding: 30px;
      }
      
      .header h1 {
        margin: 0 0 20px 0;
        font-size: 2.5em;
        font-weight: 300;
      }
      
      .test-info p {
        margin: 8px 0;
        font-size: 1.1em;
      }
      
      .summary {
        padding: 30px;
        border-bottom: 1px solid #eee;
      }
      
      .summary.failed {
        background-color: #fdf2f2;
      }
      
      .summary.passed {
        background-color: #f0fdf4;
      }
      
      .summary h2 {
        margin-top: 0;
        color: #333;
      }
      
      .summary-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
        gap: 20px;
        margin-top: 20px;
      }
      
      .summary-item {
        text-align: center;
        padding: 20px;
        border-radius: 8px;
        background: #f8fafc;
        border: 2px solid #e2e8f0;
      }
      
      .summary-item.passed {
        background: #dcfce7;
        border-color: #22c55e;
        color: #15803d;
      }
      
      .summary-item.failed {
        background: #fee2e2;
        border-color: #ef4444;
        color: #dc2626;
      }
      
      .summary-number {
        display: block;
        font-size: 2.5em;
        font-weight: bold;
        line-height: 1;
      }
      
      .summary-label {
        display: block;
        margin-top: 8px;
        font-size: 0.9em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      
      .test-results {
        padding: 30px;
      }
      
      .test-results h2 {
        margin-top: 0;
        margin-bottom: 30px;
      }
      
      .test-item {
        border: 1px solid #e5e7eb;
        border-radius: 8px;
        margin-bottom: 20px;
        overflow: hidden;
      }
      
      .test-item.passed {
        border-left: 4px solid #22c55e;
      }
      
      .test-item.failed {
        border-left: 4px solid #ef4444;
      }
      
      .test-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 20px;
        background: #f9fafb;
        flex-wrap: wrap;
        gap: 10px;
      }
      
      .test-header h3 {
        margin: 0;
        font-size: 1.2em;
        flex: 1;
      }
      
      .test-status {
        padding: 4px 12px;
        border-radius: 20px;
        font-size: 0.8em;
        font-weight: bold;
        text-transform: uppercase;
      }
      
      .test-item.passed .test-status {
        background: #dcfce7;
        color: #15803d;
      }
      
      .test-item.failed .test-status {
        background: #fee2e2;
        color: #dc2626;
      }
      
      .test-duration {
        color: #6b7280;
        font-size: 0.9em;
      }
      
      .error-details {
        padding: 20px;
        background: #fefefe;
        border-top: 1px solid #e5e7eb;
      }
      
      .error-details h4, .error-details h5, .error-details h6 {
        margin-top: 0;
        margin-bottom: 15px;
        color: #374151;
      }
      
      .error-message {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 15px;
        margin: 15px 0;
        overflow-x: auto;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 0.9em;
        line-height: 1.4;
        white-space: pre-wrap;
      }
      
      .failed-results {
        margin-top: 20px;
      }
      
      .match-results {
        margin-left: 20px;
      }
      
      .match-result {
        margin-bottom: 20px;
        padding: 15px;
        background: #fafafa;
        border-left: 3px solid #dc2626;
        border-radius: 0 6px 6px 0;
      }
      
      .match-errors, .partial-matches {
        margin-bottom: 15px;
      }
      
      .match-errors ul, .partial-matches ul {
        margin: 8px 0;
        padding-left: 20px;
      }
      
      .match-errors li, .partial-matches li {
        margin-bottom: 5px;
        color: #374151;
      }
      
      @media (max-width: 768px) {
        .container {
          margin: 10px;
          border-radius: 0;
        }
        
        .header {
          padding: 20px;
        }
        
        .header h1 {
          font-size: 2em;
        }
        
        .test-header {
          flex-direction: column;
          align-items: flex-start;
        }
        
        .summary-grid {
          grid-template-columns: 1fr;
        }
      }
      """;
  }
}
