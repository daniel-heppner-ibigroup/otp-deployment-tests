package com.arcadis.otp_deployment_tests;

import java.util.HashMap;
import java.util.Map;
import org.opentripplanner.client.model.Coordinate;

public class CoordinatesStore {

  private final Map<String, Coordinate> coordinateMap;

  public CoordinatesStore() {
    this.coordinateMap = new HashMap<>();
  }

  public Coordinate get(String key) {
    return coordinateMap.get(key);
  }

  public void add(String key, double lat, double lon) {
    coordinateMap.put(key, new Coordinate(lat, lon));
  }
}
