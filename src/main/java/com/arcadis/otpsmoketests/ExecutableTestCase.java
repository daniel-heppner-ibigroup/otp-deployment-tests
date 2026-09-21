package com.arcadis.otpsmoketests;

import java.util.Objects;

/** A named test invocation that can be executed and reported independently. */
public record ExecutableTestCase(String id, String displayName, TestBody body) {
  public ExecutableTestCase {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Test case ID must not be blank");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException(
        "Test case display name must not be blank"
      );
    }
    Objects.requireNonNull(body, "body must not be null");
  }

  public void execute() throws Throwable {
    body.run();
  }

  @FunctionalInterface
  public interface TestBody {
    void run() throws Throwable;
  }
}
