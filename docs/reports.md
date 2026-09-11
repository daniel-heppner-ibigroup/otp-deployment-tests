# Test reports

The monitoring application serves `/reports` on its existing port (8080). The
listing groups runs by deployment and suite, newest first. Each run has a stable
`/reports/{id}` URL until it expires. Failed tests expand by default; passing tests
also retain their trip requests and itineraries.

Reports show the first failed itinerary expectation, then every returned
itinerary in OTP order. Route badges summarize the legs; expandable details show
stops, times, duration, interlining, fares, and assertion match details. Matching
uses the assertion library's existing results; there is no similarity ranking.
Required legs are shown in declaration order, but the current assertion library
matches distinct legs without enforcing their travel order.

## Configuration and storage

```kdl
reports directory="results" retention-days=30
```

Both properties are optional and default to the values above. Retention must be a
positive integer. A relative directory is resolved from the application's working
directory. Mount this directory on persistent storage in containers.

Each completed suite saves one JSON snapshot by temporary-file rename. The
application renders HTML from these snapshots. After a successful save it removes
owned JSON reports older than the configured retention, measured from save time.
Cleanup errors are logged and do not discard the new report or change test
results. Cleanup happens only when a new report is saved.

Existing legacy HTML files are left untouched and are not listed or expired by
the new report store. They lack the structured request/itinerary data needed by
the new pages. No migration or separate static hosting is required for new runs.

Requests are captured through both `apiClient.plan()` and `timedPlan()`. Capture
is scoped to the synchronous test invocation and isolated across concurrent runs.
A test that starts its own threads must arrange capture explicitly; the current
test suites are synchronous. Captured parameters describe the supplied client
request. Client limitations still apply (for example, the current client does
not send `banned` or `walkSpeed` to OTP). Response times retain their UTC offsets.

Ordinary assertion failures, request errors, and suite-construction failures are
reported with technical details. Suite-construction failures appear as a failed
“Suite setup” entry. Saving a report does not affect the runner's metrics or test
status; save errors are logged separately.

## Assertion-library dependency

This feature uses companion commit `9bc9c3e` in `otp-java-client`: the existing
`ItineraryAssertionError` now exposes `getExpectedLegs()`,
`isStrictTransitMatching()`, and `getTripPlan()`. The old constructor remains
available. The fluent assertions and stop-on-first-failure behavior are unchanged.
Fare criteria now identify their rider category and medium as well as price.

The application currently targets the locally built `2.0.2-SNAPSHOT` client. To
build with the companion checkout (without tests):

```sh
cd ../otp-java-client
mvn -Dmaven.test.skip=true -Dgpg.skip=true -Dmaven.javadoc.skip=true install
cd ../otp-deployment-tests
mvn -Dmaven.test.skip=true -Dplugin.prettier.skip=true package
```

A clean CI or Docker build needs a published version containing the companion
change, followed by an update to `otp.client.version` in `pom.xml`. Nothing is
published by this implementation.
