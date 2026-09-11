package com.arcadis.otpsmoketests.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One atomic JSON document per run. Only files owned by this store are expired. */
public final class ReportStore {

  private static final Logger LOG = LoggerFactory.getLogger(ReportStore.class);
  private static final Pattern ID = Pattern.compile(
    "[0-9]{13}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  );
  private final Path directory;
  private final Duration retention;
  private final Clock clock;
  private final ObjectMapper mapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public record Entry(
    String id,
    Instant startedAt,
    String deployment,
    String suite,
    long durationMs,
    long passed,
    long failed
  ) {}

  public ReportStore(Path directory, int retentionDays) throws IOException {
    this(directory, retentionDays, Clock.systemUTC());
  }

  public ReportStore(Path directory, int retentionDays, Clock clock)
    throws IOException {
    if (retentionDays < 1) throw new IllegalArgumentException(
      "retentionDays must be positive"
    );
    this.directory = directory.toAbsolutePath().normalize();
    this.retention = Duration.ofDays(retentionDays);
    this.clock = clock;
    Files.createDirectories(this.directory);
  }

  public String newId() {
    return clock.millis() + "-" + UUID.randomUUID();
  }

  public void save(Report report) throws IOException {
    Path destination = path(report.id());
    Path temporary = Files.createTempFile(directory, ".report-", ".tmp");
    try {
      mapper.writeValue(temporary.toFile(), report);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, destination);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
    // A cleanup failure must never turn a successfully saved report into a failed save.
    try {
      deleteExpired();
    } catch (IOException e) {
      LOG.warn("Could not clean up expired reports in {}", directory, e);
    }
  }

  public Optional<Report> load(String id) throws IOException {
    if (!ID.matcher(id).matches()) return Optional.empty();
    Path file = path(id);
    if (
      !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
    ) return Optional.empty();
    try {
      Report report = mapper.readValue(file.toFile(), Report.class);
      if (!id.equals(report.id())) throw new IOException(
        "Report ID does not match its filename"
      );
      return Optional.of(report);
    } catch (NoSuchFileException | java.io.FileNotFoundException e) {
      return Optional.empty();
    }
  }

  public List<Entry> list() throws IOException {
    List<Entry> entries = new ArrayList<>();
    try (var files = Files.newDirectoryStream(directory, "*.json")) {
      for (Path file : files) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - 5);
        try {
          load(id)
            .ifPresent(report ->
              entries.add(
                new Entry(
                  report.id(),
                  report.startedAt(),
                  report.deployment(),
                  report.suite(),
                  report.durationMs(),
                  report.tests().size() - report.failedCount(),
                  report.failedCount()
                )
              )
            );
        } catch (IOException | RuntimeException e) {
          LOG.warn("Skipping unreadable report {}", file, e);
        }
      }
    }
    entries.sort(
      Comparator.comparing(Entry::startedAt).reversed().thenComparing(Entry::id)
    );
    return entries;
  }

  private void deleteExpired() throws IOException {
    long cutoff = clock.instant().minus(retention).toEpochMilli();
    try (var files = Files.newDirectoryStream(directory, "*.json")) {
      for (Path file : files) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - 5);
        if (
          ID.matcher(id).matches() &&
          Long.parseLong(id.substring(0, 13)) < cutoff &&
          Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        ) {
          try {
            Files.deleteIfExists(file);
          } catch (IOException e) {
            LOG.warn("Could not delete expired report {}", file, e);
          }
        }
      }
    }
  }

  private Path path(String id) {
    if (!ID.matcher(id).matches()) throw new IllegalArgumentException(
      "Invalid report ID"
    );
    return directory.resolve(id + ".json");
  }
}
