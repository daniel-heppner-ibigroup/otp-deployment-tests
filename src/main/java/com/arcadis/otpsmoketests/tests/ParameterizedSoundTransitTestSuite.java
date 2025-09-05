package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestItinerary;
import java.io.IOException;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.parameters.TripPlanParameters;
import org.opentripplanner.client.parameters.TripPlanParametersBuilder;

@Tag("smoke-test")
@Tag("soundtransit")
@DisplayName("Parameterized Sound Transit Smoke Tests")
public class ParameterizedSoundTransitTestSuite extends BaseTestSuite {

  public ParameterizedSoundTransitTestSuite(String baseUrl) {
    super(
      "SoundTransit",
      baseUrl,
      "https://87sp37ezga.execute-api.us-east-1.amazonaws.com/st/autocomplete",
      47.61097,
      -122.33701
    );
  }

  public static TripPlanParametersBuilder defaultBuilder() {
    return TripPlanParameters
      .builder()
      .withTime(weekdayAtNoon())
      .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
      .withModes(Set.of(RequestMode.TRANSIT, RequestMode.WALK));
  }

  @Override
  protected void initializeCoordinates() {
    // Keep existing coordinates
    geocoder.add("LYNNWOOD_STA", 47.8154272, -122.2940715);
    geocoder.add("SODO", 47.5811, -122.3290);
    geocoder.add("CLYDE_HILL", 47.6316, -122.2173);
    geocoder.add("RONALD_BOG_PARK", 47.75601664, -122.33141);
    geocoder.add("ESPERANCE", 47.797330, -122.351560592);
    geocoder.add("SHORELINE", 47.7568, -122.3483);
    geocoder.add("MOUNTAINLAKE_TERRACE", 47.7900, -122.30379581);
    geocoder.add("OLIVE_WAY", 47.61309420, -122.336314916);
    geocoder.add("CASINO_RD", 47.92175434762228, -122.23896905562611);
    geocoder.add("MARYSVILLE", 48.05523331013222, -122.17763080699298);
    try {
      geocoder.addGeocoded(
        "NORTHGATE_MALL",
        "Northgate Mall, Seattle",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "UW_STATION",
        "University of Washington Station",
        "openstreetmap"
      );
      geocoder.addGeocoded("colman dock", "Colman Dock", "openstreetmap");
      geocoder.addGeocoded(
        "paul allen center",
        "paul allen center",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "king st station",
        "King St Station",
        "openstreetmap"
      );
      geocoder.addGeocoded("tacoma dome", "Tacoma Dome", "openstreetmap");
      geocoder.addGeocoded(
        "45th veg thai",
        "45th Vegetarian Thai",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "s bellevue station",
        "South Bellevue Station",
        "otp"
      );
      geocoder.addGeocoded("climate pledge", "Climate Pledge", "openstreetmap");
      geocoder.addGeocoded("1st and lander", "1st ave s and s lander", "otp");
      geocoder.addGeocoded(
        "e prospect and broadway",
        "East Prospect Street & Broadway East",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "hopelink food bank",
        "Hopelink Food Bank",
        "openstreetmap"
      );
      geocoder.addGeocoded("queen anne", "1321 W Emerson St", "openaddresses");
      geocoder.addGeocoded(
        "tractor tavern ballard",
        "Tractor Tavern",
        "openstreetmap"
      );
      geocoder.addGeocoded("mukilteo ferry", "Mukilteo Ferry", "openstreetmap");
      geocoder.addGeocoded("green lake", "Little Red Hen", "openstreetmap");
      geocoder.addGeocoded("capitol hill", "Capitol Hill", "otp");
      geocoder.addGeocoded("paseo fremont", "N 43rd St & Fremont Ave N", "otp");
      geocoder.addGeocoded("columbia city", "columbia city", "otp");
      geocoder.addGeocoded(
        "1801 s bush pl",
        "1801 S Bush Place",
        "openaddresses"
      );
      geocoder.addGeocoded(
        "bellevue transit center",
        "Bellevue Transit Center",
        "otp"
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to geocode addresses", e);
    }
  }

  @Override
  protected TripPlanParameters getDefaultTripPlanParameters() {
    // Sound Transit specific default parameters
    return TripPlanParameters.builder().withWalkReluctance(15).build();
  }

  @Test
  @DisplayName("Test E Line bus from Olive Way to Shoreline")
  public void testELineBus() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("OLIVE_WAY"))
      .withTo(geocoder.get("SHORELINE"))
      .build();

    var plan = apiClient.timedPlan(params, "testELineBus");

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("E Line")
      .withFarePrice(2.75f, "orca:regular", "orca:cash")
      .withFarePrice(1.0f, "orca:senior", "orca:cash")
      .withFarePrice(1.0f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  // Add a few key test methods to demonstrate it works
  @Test
  @DisplayName("Test 1 Line light rail from Ronald Bog Park to Olive Way")
  public void testLightRail() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("RONALD_BOG_PARK"))
      .withTo(geocoder.get("OLIVE_WAY"))
      .build();

    var plan = apiClient.timedPlan(params, "testLightRail");

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("TRAM")
      .withRouteShortName("1 Line")
      .withFarePrice(3.0f, "orca:regular", "orca:cash")
      .withFarePrice(1.0f, "orca:senior", "orca:cash")
      .withFarePrice(1.0f, "orca:special", "orca:electronic")
      .assertMatches();
  }
}
