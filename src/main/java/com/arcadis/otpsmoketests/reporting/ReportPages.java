package com.arcadis.otpsmoketests.reporting;

import static j2html.TagCreator.*;

import j2html.tags.DomContent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Server-rendered reports. Every value from tests or OTP is escaped by j2html. */
public final class ReportPages {

  private ReportPages() {}

  public static String index(List<ReportStore.Entry> reports) {
    var body = main()
      .with(
        h1("Test reports"),
        p("Deployment / suite / run").withClass("muted")
      );
    Map<String, Map<String, List<ReportStore.Entry>>> groups = new TreeMap<>();
    for (ReportStore.Entry report : reports) {
      groups
        .computeIfAbsent(report.deployment(), key -> new TreeMap<>())
        .computeIfAbsent(report.suite(), key -> new java.util.ArrayList<>())
        .add(report);
    }
    if (reports.isEmpty()) body.with(
      p("No reports yet. Completed runs will appear here.")
    );
    groups.forEach((deployment, suites) -> {
      var group = section(h2(deployment));
      suites.forEach((suite, runs) -> {
        var rows = tbody();
        runs
          .stream()
          .sorted(
            java.util.Comparator
              .comparing(ReportStore.Entry::startedAt)
              .reversed()
          )
          .forEach(run ->
            rows.with(
              tr(
                td(
                  a(run.startedAt().toString()).withHref("/reports/" + run.id())
                ),
                td(status(run.failed() == 0)),
                td(run.passed() + " passed / " + run.failed() + " failed"),
                td(run.durationMs() + " ms")
              )
            )
          );
        group.with(
          h3(suite),
          div(
            table(
              thead(
                tr(th("Run (UTC)"), th("Status"), th("Tests"), th("Duration"))
              ),
              rows
            )
          )
            .withClass("table-wrap")
        );
      });
      body.with(group);
    });
    return page("Test reports", body);
  }

  public static String report(Report report) {
    var body = main(
      a("← All reports").withHref("/reports"),
      p(report.deployment()).withClass("eyebrow"),
      h1(report.suite()),
      p(report.startedAt() + " · " + report.durationMs() + " ms")
        .withClass("muted"),
      p(report.baseUrl()),
      div(
        status(report.failedCount() == 0),
        span((report.tests().size() - report.failedCount()) + " passed"),
        span(report.failedCount() + " failed")
      )
        .withClass("summary-bar")
    );
    for (int i = 0; i < report.tests().size(); i++) body.with(
      test(report.tests().get(i), i)
    );
    return page(report.suite() + " · " + report.deployment(), body);
  }

  private static DomContent test(Report.Test test, int index) {
    var content = div().withClass("test-content");
    if (!test.passed()) {
      content.with(
        h3(
          test.expectation() == null
            ? "Test failed"
            : "No itinerary satisfies this expectation"
        )
      );
      if (test.expectation() == null) content.with(
        pre(
          test.errorMessage() == null
            ? "No error message provided."
            : test.errorMessage()
        )
      );
    }
    Report.Expectation expectation = test.expectation();
    if (expectation != null) {
      var expected = div(h3("Expected legs")).withClass("expected");
      var legs = div().withClass("journey");
      for (int i = 0; i < expectation.legs().size(); i++) {
        legs.with(
          div(
            span("Requirement " + (i + 1)).withClass("muted"),
            ul(each(expectation.legs().get(i), criterion -> li(criterion)))
          )
            .withClass("expected-leg")
        );
      }
      expected.with(
        legs,
        p(
          expectation.strictTransitMatching()
            ? "Every required leg must match. Additional transit legs are not allowed."
            : "Every required leg must match. Additional transit legs are allowed."
        )
      );
      // The assertion library matches distinct legs, not necessarily their sequence.
      expected.with(
        p(
          "Requirements are shown in declaration order; matching does not enforce travel order."
        )
          .withClass("muted")
      );
      content.with(expected);
    }
    for (int r = 0; r < test.requests().size(); r++) {
      Report.Request request = test.requests().get(r);
      boolean failedRequest =
        expectation != null && expectation.requestIndex() == r;
      var block = section(
        h3(
          "Request " + (r + 1) + (failedRequest ? " · failed expectation" : "")
        )
      );
      var parameters = dl();
      request
        .parameters()
        .forEach((key, value) -> parameters.with(dt(key), dd(value)));
      parameters.with(dt("Client time zone"), dd(request.timeZone()));
      block.with(details(summary("Trip request parameters"), parameters));
      if (request.error() != null) block.with(
        p(request.error()).withClass("error")
      );
      if (
        request.itineraries().isEmpty() && request.error() == null
      ) block.with(p("OTP returned no itineraries."));
      for (int i = 0; i < request.itineraries().size(); i++) {
        Report.Match match = failedRequest && i < expectation.matches().size()
          ? expectation.matches().get(i)
          : null;
        block.with(journey(request.itineraries().get(i), i, match));
      }
      content.with(block);
    }
    if (test.requests().isEmpty()) content.with(
      p("No trip requests were captured for this test.").withClass("muted")
    );
    if (test.errorMessage() != null || test.stackTrace() != null) {
      content.with(
        details(
          summary("Technical error details"),
          pre(
            test.stackTrace() == null ? test.errorMessage() : test.stackTrace()
          )
        )
      );
    }
    var result = details(
      summary(
        status(test.passed()),
        strong(test.name()),
        span(test.durationMs() + " ms").withClass("muted")
      ),
      content
    )
      .withClass("test")
      .withId("test-" + index);
    if (!test.passed()) result.attr("open");
    return result;
  }

  private static DomContent journey(
    Report.Journey journey,
    int index,
    Report.Match match
  ) {
    var sequence = div(
      span("Itinerary " + (index + 1)).withClass("journey-number")
    )
      .withClass("journey");
    for (int i = 0; i < journey.legs().size(); i++) {
      Report.Leg leg = journey.legs().get(i);
      if (i > 0) sequence.with(
        span("→").withClass("arrow").attr("aria-hidden", "true")
      );
      sequence.with(
        span(leg.route()).withClass("route " + legStatus(match, i))
      );
    }
    var legDetails = div();
    for (int i = 0; i < journey.legs().size(); i++) {
      Report.Leg leg = journey.legs().get(i);
      var item = div(
        h4("Leg " + (i + 1) + " · " + leg.route() + " · " + leg.mode()),
        p(leg.from() + " → " + leg.to()),
        p(leg.departure() + " → " + leg.arrival() + " · " + leg.duration())
      )
        .withClass("leg");
      if (
        leg.routeLongName() != null && !leg.routeLongName().equals(leg.route())
      ) {
        item.with(p("Route name: " + leg.routeLongName()));
      }
      if (leg.interlined()) item.with(p("Interlined with previous leg"));
      for (Report.Fare fare : leg.fares()) item.with(
        p(
          "Fare: " +
          fare.amount() +
          " " +
          fare.currency() +
          " · " +
          fare.name() +
          " · Rider: " +
          fare.rider() +
          " · Medium: " +
          fare.medium()
        )
      );
      if (leg.fares().isEmpty()) item.with(
        p("No fare products returned.").withClass("muted")
      );
      if (match != null) {
        final int legIndex = i;
        match
          .legs()
          .stream()
          .filter(m -> m.legIndex() == legIndex)
          .forEach(m -> {
            item.with(p(m.status()).withClass(m.status()));
            if (!m.matched().isBlank()) item.with(p("Matched: " + m.matched()));
            if (!m.missing().isBlank()) item.with(
              p("Missing: " + m.missing()).withClass("error")
            );
          });
      }
      legDetails.with(item);
    }
    var row = div(sequence).withClass("itinerary");
    if (match != null) row.with(
      ul(each(match.errors(), error -> li(error))).withClass("error")
    );
    return row.with(details(summary("Leg details & fares"), legDetails));
  }

  private static String legStatus(Report.Match match, int index) {
    if (match == null) return "";
    // A leg may partially satisfy one expectation and fully satisfy another. Retain both in details.
    if (
      match
        .legs()
        .stream()
        .anyMatch(m -> m.legIndex() == index && m.status().equals("matched"))
    ) return "matched";
    if (
      match
        .legs()
        .stream()
        .anyMatch(m -> m.legIndex() == index && m.status().equals("partial"))
    ) return "partial";
    if (
      match
        .legs()
        .stream()
        .anyMatch(m -> m.legIndex() == index && m.status().equals("extra"))
    ) return "extra";
    return "";
  }

  private static DomContent status(boolean passed) {
    return span(passed ? "Passed" : "Failed")
      .withClass("status " + (passed ? "passed" : "failed"));
  }

  private static String page(String titleText, DomContent content) {
    return (
      "<!doctype html>" +
      html(
        head(
          meta().withCharset("utf-8"),
          meta()
            .withName("viewport")
            .withContent("width=device-width, initial-scale=1"),
          title(titleText),
          link().withRel("stylesheet").withHref("/reports/assets/report.css")
        ),
        body(
          header(a("OTP / OPERATIONS").withHref("/reports")),
          content,
          footer("OTP smoke tests · All run timestamps in UTC")
        )
      )
        .attr("lang", "en")
        .render()
    );
  }
}
