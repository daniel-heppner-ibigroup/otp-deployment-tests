package com.arcadis.otpsmoketests.configuration;

import com.arcadis.otpsmoketests.BaseTestSuite;
import java.util.Collection;

public record Configuration(
  Collection<DeploymentUnderTest> deploymentsUnderTest
) {
  public record DeploymentUnderTest(
    String name,
    String url,
    Collection<TestSuite> suites
  ) {}

  public record TestSuite(
    String name,
    Class<BaseTestSuite> clazz,
    String interval
  ) {}
}
