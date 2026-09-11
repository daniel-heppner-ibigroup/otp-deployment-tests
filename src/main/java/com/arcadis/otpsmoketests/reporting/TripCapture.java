package com.arcadis.otpsmoketests.reporting;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

/** A scope owned by the runner, isolated between concurrent scheduled and manual runs. */
public final class TripCapture implements AutoCloseable {

  private static final ThreadLocal<TripCapture> CURRENT = new ThreadLocal<>();
  private final List<CapturedRequest> requests = new ArrayList<>();

  public record CapturedRequest(
    Map<String, String> parameters,
    String timeZone,
    TripPlan plan,
    String error
  ) {}

  private TripCapture() {}

  public static TripCapture begin() {
    if (CURRENT.get() != null) throw new IllegalStateException(
      "A capture is already active"
    );
    TripCapture capture = new TripCapture();
    CURRENT.set(capture);
    return capture;
  }

  public static void record(
    TripPlanParameters parameters,
    ZoneId zone,
    TripPlan plan,
    Throwable error
  ) {
    TripCapture capture = CURRENT.get();
    if (capture != null) {
      capture.requests.add(
        new CapturedRequest(
          snapshot(parameters),
          zone.toString(),
          plan,
          error == null
            ? null
            : error.getClass().getSimpleName() + ": " + error.getMessage()
        )
      );
    }
  }

  public List<CapturedRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    CURRENT.remove();
  }

  private static Map<String, String> snapshot(TripPlanParameters p) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("From", p.fromPlace().toPlaceString());
    values.put("To", p.toPlace().toPlaceString());
    values.put("Date / time", p.time().toString());
    values.put("Direction", p.searchDirection().toString());
    values.put(
      "Modes",
      p
        .modes()
        .stream()
        .map(Object::toString)
        .sorted()
        .collect(java.util.stream.Collectors.joining(", "))
    );
    values.put("Requested itineraries", Integer.toString(p.numItineraries()));
    values.put("Wheelchair", Boolean.toString(p.wheelchair()));
    values.put("Optimize", String.valueOf(p.optimize()));
    put(values, "Search window", p.searchWindow());
    put(values, "Walk reluctance", p.walkReluctance());
    put(values, "Car reluctance", p.carReluctance());
    put(values, "Bike reluctance", p.bikeReluctance());
    put(values, "Bike walking reluctance", p.bikeWalkingReluctance());
    put(values, "Walk speed", p.walkSpeed());
    put(values, "Banned", p.banned());
    put(
      values,
      "Triangle",
      p.triangle().map(triangle -> triangle.toGenegerated().toString())
    );
    put(values, "Page cursor", p.pageCursor());
    return values;
  }

  private static void put(
    Map<String, String> values,
    String key,
    Optional<?> value
  ) {
    value.ifPresent(v -> values.put(key, v.toString()));
  }
}
