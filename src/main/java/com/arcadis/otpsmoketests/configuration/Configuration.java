package com.arcadis.otpsmoketests.configuration;

import com.arcadis.otpsmoketests.BaseTestSuite;
import java.util.Collection;

public class Configuration {

  private ReportSettings reports = new ReportSettings("results", 30);

  public record ReportSettings(String directory, int retentionDays) {
    public ReportSettings {
      if (directory == null || directory.isBlank()) {
        throw new IllegalArgumentException(
          "Report directory must not be blank"
        );
      }
      if (retentionDays < 1) {
        throw new IllegalArgumentException(
          "Report retention-days must be positive"
        );
      }
    }
  }

  public ReportSettings getReports() {
    return reports;
  }

  public void setReports(ReportSettings reports) {
    this.reports = reports;
  }

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
