package com.arcadis.otpsmoketests.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.opentripplanner.client.model.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeocodingService {

  /**
   * Builder for creating GeocodingService instances.
   */
  public static class Builder {

    private String peliasBaseUrl;
    private String layers = "address,venue,street,intersection"; // default value
    private double focusLat;
    private double focusLon;

    public Builder() {}

    public Builder peliasBaseUrl(String peliasBaseUrl) {
      this.peliasBaseUrl = peliasBaseUrl;
      return this;
    }

    public Builder layers(String layers) {
      this.layers = layers;
      return this;
    }

    public Builder focusPoint(double lat, double lon) {
      this.focusLat = lat;
      this.focusLon = lon;
      return this;
    }

    public Builder focusLat(double focusLat) {
      this.focusLat = focusLat;
      return this;
    }

    public Builder focusLon(double focusLon) {
      this.focusLon = focusLon;
      return this;
    }

    public GeocodingService build() {
      if (peliasBaseUrl == null || peliasBaseUrl.trim().isEmpty()) {
        throw new IllegalArgumentException("peliasBaseUrl is required");
      }

      return new GeocodingService(peliasBaseUrl, layers, focusLat, focusLon);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(
    GeocodingService.class
  );

  private final String peliasBaseUrl;
  private final String layers;
  private final double focusLat;
  private final double focusLon;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Map<String, Coordinate> coordinateMap;

  public GeocodingService(
    String peliasBaseUrl,
    String layers,
    double focusLat,
    double focusLon
  ) {
    this.peliasBaseUrl = peliasBaseUrl;
    this.layers = layers;
    this.focusLat = focusLat;
    this.focusLon = focusLon;
    this.httpClient = new OkHttpClient();
    this.objectMapper = new ObjectMapper();
    this.coordinateMap = new HashMap<>();
  }

  /**
   * Creates a new builder for constructing GeocodingService instances.
   *
   * @return A new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets a stored coordinate by key.
   *
   * @param key The key to look up
   * @return The coordinate if found, null if not found
   */
  public Coordinate get(String key) {
    return coordinateMap.get(key);
  }

  /**
   * Stores raw coordinates.
   *
   * @param key The key to store the coordinate under
   * @param lat The latitude
   * @param lon The longitude
   */
  public void add(String key, double lat, double lon) {
    coordinateMap.put(key, new Coordinate(lat, lon));
    logger.debug("Stored coordinates for {}: ({}, {})", key, lat, lon);
  }

  /**
   * Geocodes an address using the Pelias geocoding service, filtering by source.
   *
   * @param address The address to geocode
   * @param source The source to filter by (e.g., "openstreetmap", "geonames"), or null for any source
   * @return Optional containing the coordinate if found, empty if not found
   * @throws IOException if there's an error communicating with the geocoding service
   */
  public Optional<Coordinate> geocode(String address, String source)
    throws IOException {
    HttpUrl url = HttpUrl
      .parse(peliasBaseUrl)
      .newBuilder()
      .addQueryParameter("text", address)
      .addQueryParameter("layers", layers)
      .addQueryParameter("focus.point.lat", String.valueOf(focusLat))
      .addQueryParameter("focus.point.lon", String.valueOf(focusLon))
      .build();

    Request request = new Request.Builder().url(url).build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        logger.error(
          "Failed to geocode address: {}, status: {}",
          address,
          response.code()
        );
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(response.body().string());
      JsonNode features = root.path("features");

      if (features.isEmpty()) {
        logger.warn("No results found for address: {}", address);
        return Optional.empty();
      }

      // Find the first feature that matches the source filter (or the first feature if no filter)
      JsonNode matchingFeature = null;
      for (JsonNode feature : features) {
        JsonNode properties = feature.path("properties");
        String featureSource = properties.path("source").asText();

        // If no source filter or the source matches
        if (source == null || source.equals(featureSource)) {
          matchingFeature = feature;
          break;
        }
      }

      if (matchingFeature == null) {
        logger.warn(
          "No results found for address: {} with source: {}",
          address,
          source
        );
        return Optional.empty();
      }

      JsonNode coordinates = matchingFeature
        .path("geometry")
        .path("coordinates");

      if (coordinates.isArray() && coordinates.size() >= 2) {
        double lon = coordinates.get(0).asDouble();
        double lat = coordinates.get(1).asDouble();

        // Log the source of the result
        String resultSource = matchingFeature
          .path("properties")
          .path("source")
          .asText();
        logger.debug(
          "Found coordinates for '{}' from source '{}': ({}, {})",
          address,
          resultSource,
          lat,
          lon
        );

        return Optional.of(new Coordinate(lat, lon));
      }

      logger.warn("Invalid coordinates in response for address: {}", address);
      return Optional.empty();
    }
  }

  /**
   * Geocodes an address and stores it in the CoordinatesStore, filtering by source.
   *
   * @param key The key to store the coordinate under
   * @param address The address to geocode
   * @param source The source to filter by (e.g., "openstreetmap", "geonames"), or null for any source
   * @throws IOException if there's an error communicating with the geocoding service
   * @throws IllegalArgumentException if the address cannot be geocoded
   */
  public void addGeocoded(String key, String address, String source)
    throws IOException {
    Coordinate coordinate = geocode(address, source)
      .orElseThrow(() ->
        new IllegalArgumentException(
          "Could not geocode address: " +
          address +
          (source != null ? " with source: " + source : "")
        )
      );

    coordinateMap.put(key, coordinate);
    logger.debug(
      "Geocoded and stored {} at ({}, {}): {}{}",
      key,
      coordinate.lat(),
      coordinate.lon(),
      address,
      source != null ? " (source: " + source + ")" : ""
    );
  }
}
