package com.arcadis.otpsmoketests.tests;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

@Tag("smoke-test")
@Tag("metro-transit-minneapolis")
@DisplayName("Metro Transit Minneapolis Smoke Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class MetroTransitMinneapolisTestSuite extends JsonTestSuite {

  private static final String RESOURCE_PATH =
    "test-suites/metro-transit-minneapolis.json";

  public MetroTransitMinneapolisTestSuite(
    String baseUrl,
    String deploymentName
  ) {
    super(
      deploymentName,
      baseUrl,
      "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
      44.9778,
      -93.2650,
      ZoneId.of("America/Chicago"),
      RESOURCE_PATH
    );
  }
}
