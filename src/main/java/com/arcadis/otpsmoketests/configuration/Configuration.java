package com.arcadis.otpsmoketests.configuration;

import com.arcadis.otpsmoketests.BaseTestSuite;
import java.util.Collection;

public class Configuration {

  public Collection<DeploymentUnderTest> deploymentsUnderTest;

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

  public Collection<DeploymentUnderTest> getDeploymentsUnderTest() {
    return deploymentsUnderTest;
  }

  public void setDeploymentsUnderTest(
    Collection<DeploymentUnderTest> deploymentsUnderTest
  ) {
    this.deploymentsUnderTest = deploymentsUnderTest;
  }
}
