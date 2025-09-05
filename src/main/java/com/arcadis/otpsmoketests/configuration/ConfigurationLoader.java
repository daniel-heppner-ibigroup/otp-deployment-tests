package com.arcadis.otpsmoketests.configuration;

import com.arcadis.otpsmoketests.BaseTestSuite;
import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationLoader {

  public static Configuration loadFromFile(String filePath)
    throws IOException, KdlParseException {
    String content = Files.readString(Path.of(filePath));
    return loadFromString(content);
  }

  public static Configuration loadFromString(String kdlContent)
          throws KdlParseException, IOException {
    KdlParser parser = KdlParser.v2();
    KdlDocument document = parser.parse(kdlContent);
    Configuration config = new Configuration();

    List<Configuration.DeploymentUnderTest> deployments = new ArrayList<>();

    for (KdlNode node : document.nodes()) {
      if ("deployment".equals(node.name())) {
        deployments.add(parseDeployment(node));
      }
    }

    config.setDeploymentsUnderTest(deployments);
    return config;
  }

  private static Configuration.DeploymentUnderTest parseDeployment(
    KdlNode deploymentNode
  ) {
    String name = deploymentNode.getProperty("name").get().value().toString();
    String url = deploymentNode.getProperty("url").get().value().toString();

    List<Configuration.TestSuite> suites = new ArrayList<>();

    for (KdlNode childNode : deploymentNode.children()) {
      if ("test-suite".equals(childNode.name())) {
        suites.add(parseTestSuite(childNode));
      }
    }

    return new Configuration.DeploymentUnderTest(name, url, suites);
  }

  private static Configuration.TestSuite parseTestSuite(KdlNode testSuiteNode) {
    String name = testSuiteNode.getProperty("name").get().value().toString();
    String className = testSuiteNode.getProperty("class").get().value().toString();
    String interval = testSuiteNode.getProperty("interval").get().value().toString();

    Class<BaseTestSuite> clazz = resolveTestSuiteClass(className);

    return new Configuration.TestSuite(name, clazz, interval);
  }

  @SuppressWarnings("unchecked")
  private static Class<BaseTestSuite> resolveTestSuiteClass(String className) {
    try {
      String fullClassName;
      if (className.contains(".")) {
        fullClassName = className;
      } else {
        fullClassName = "com.arcadis.otpsmoketests.tests." + className;
        if (!className.endsWith("TestSuite")) {
          fullClassName += "TestSuite";
        }
      }

      Class<?> clazz = Class.forName(fullClassName);
      if (BaseTestSuite.class.isAssignableFrom(clazz)) {
        return (Class<BaseTestSuite>) clazz;
      } else {
        throw new IllegalArgumentException(
          "Class " + fullClassName + " does not extend BaseTestSuite"
        );
      }
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
        "Test suite class not found: " + className,
        e
      );
    }
  }
}
