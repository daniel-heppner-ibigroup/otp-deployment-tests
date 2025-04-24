package com.arcadis.otp_deployment_tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.opentripplanner.client.model.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class GeocodingService {
    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);
    private static final String PELIAS_BASE_URL = "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete";
    private static final double DEFAULT_FOCUS_LAT = 47.61097;
    private static final double DEFAULT_FOCUS_LON = -122.33701;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CoordinatesStore store;

    public GeocodingService(CoordinatesStore store) {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.store = store;
    }

    /**
     * Gets the associated CoordinatesStore.
     *
     * @return The CoordinatesStore being used by this service
     */
    public CoordinatesStore getStore() {
        return store;
    }

    /**
     * Stores raw coordinates in the CoordinatesStore.
     *
     * @param key The key to store the coordinate under
     * @param lat The latitude
     * @param lon The longitude
     */
    public void storeCoordinate(String key, double lat, double lon) {
        store.add(key, lat, lon);
        logger.debug("Stored coordinates for {}: ({}, {})", key, lat, lon);
    }

    /**
     * Geocodes an address using the Pelias geocoding service.
     *
     * @param address The address to geocode
     * @return Optional containing the coordinate if found, empty if not found
     * @throws IOException if there's an error communicating with the geocoding service
     */
    public Optional<Coordinate> geocode(String address) throws IOException {
        return geocode(address, null);
    }

    /**
     * Geocodes an address using the Pelias geocoding service, filtering by source.
     *
     * @param address The address to geocode
     * @param source The source to filter by (e.g., "openstreetmap", "geonames"), or null for any source
     * @return Optional containing the coordinate if found, empty if not found
     * @throws IOException if there's an error communicating with the geocoding service
     */
    public Optional<Coordinate> geocode(String address, String source) throws IOException {
        HttpUrl url = HttpUrl.parse(PELIAS_BASE_URL).newBuilder()
                .addQueryParameter("text", address)
                .addQueryParameter("layers", "address,venue,street,intersection")
                .addQueryParameter("focus.point.lat", String.valueOf(DEFAULT_FOCUS_LAT))
                .addQueryParameter("focus.point.lon", String.valueOf(DEFAULT_FOCUS_LON))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                logger.error("Failed to geocode address: {}, status: {}", address, response.code());
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
                logger.warn("No results found for address: {} with source: {}", address, source);
                return Optional.empty();
            }

            JsonNode coordinates = matchingFeature.path("geometry").path("coordinates");

            if (coordinates.isArray() && coordinates.size() >= 2) {
                double lon = coordinates.get(0).asDouble();
                double lat = coordinates.get(1).asDouble();
                
                // Log the source of the result
                String resultSource = matchingFeature.path("properties").path("source").asText();
                logger.debug("Found coordinates for '{}' from source '{}': ({}, {})", 
                        address, resultSource, lat, lon);
                
                return Optional.of(new Coordinate(lat, lon));
            }

            logger.warn("Invalid coordinates in response for address: {}", address);
            return Optional.empty();
        }
    }

    /**
     * Geocodes an address and stores it in the CoordinatesStore.
     *
     * @param key The key to store the coordinate under
     * @param address The address to geocode
     * @throws IOException if there's an error communicating with the geocoding service
     * @throws IllegalArgumentException if the address cannot be geocoded
     */
    public void geocodeAndStore(String key, String address) throws IOException {
        geocodeAndStore(key, address, null);
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
    public void geocodeAndStore(String key, String address, String source) throws IOException {
        Coordinate coordinate = geocode(address, source)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Could not geocode address: " + address + 
                    (source != null ? " with source: " + source : "")));
        
        store.add(key, coordinate.lat(), coordinate.lon());
        logger.debug("Geocoded and stored {} at ({}, {}): {}{}", 
                key, coordinate.lat(), coordinate.lon(), address,
                source != null ? " (source: " + source + ")" : "");
    }
} 