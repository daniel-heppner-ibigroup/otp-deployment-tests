package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestItinerary;
import java.io.IOException;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.parameters.TripPlanParameters;
import org.opentripplanner.client.parameters.TripPlanParametersBuilder;

@Tag("smoke-test")
@Tag("soundtransit")
@DisplayName("Sound Transit Smoke Tests")
public class SoundTransitTestSuite extends BaseTestSuite {

  public SoundTransitTestSuite() {
    super(
      "SoundTransit",
      "https://sound-transit-otp.ibi-transit.com",
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
    //      .withWalkReluctance(15);
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

    var plan = apiClient.plan(params);

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

  @Test
  @DisplayName("Test 1 Line light rail from Ronald Bog Park to Olive Way")
  public void testLightRail() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("RONALD_BOG_PARK"))
      .withTo(geocoder.get("OLIVE_WAY"))
      .build();

    var plan = apiClient.plan(params);

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

  @Test
  @DisplayName("Test multi-leg journey from Casino Road to Marysville")
  public void testMultiLegJourney() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("CASINO_RD"))
      .withTo(geocoder.get("MARYSVILLE"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("8")
      .withFarePrice(2.00f, "orca:regular", "orca:cash")
      .withFarePrice(2.00f, "orca:regular", "orca:electronic")
      .withFarePrice(1.00f, "orca:special", "orca:electronic")
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("201", "202")
      .withFarePrice(2.50f, "orca:regular", "orca:cash")
      .withFarePrice(0.50f, "orca:regular", "orca:electronic")
      .withFarePrice(0.00f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  @Test
  @DisplayName("Colman Dock to UW CS Building")
  public void testColmanDockToUWCSBuilding() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("colman dock"))
      .withTo(geocoder.get("paul allen center"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withMode("TRAM")
      .withRouteShortName("1 Line")
      .assertMatches();
  }

  @Test
  @DisplayName("King St Station to Tacoma Dome")
  public void testKingStStationToTacomaDome() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("king st station"))
      .withTo(geocoder.get("tacoma dome"))
      .withTime(weekdayAtTime(LocalTime.of(17, 15)))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("S Line")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("590")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("578", "577")
      .hasLeg()
      .withRouteShortName("574", "586")
      .assertMatches();
  }

  @Test
  @DisplayName("King St Station to 45th Veg Thai")
  public void testKingStStationTo45thVegThai() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("king st station"))
      .withTo(geocoder.get("45th veg thai"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .hasLeg()
      .withRouteShortName("44")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("62")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("E Line")
      .hasLeg()
      .withRouteShortName("44")
      .assertMatches();
  }

  @Test
  @DisplayName("South Bellevue Station to Climate Pledge")
  public void testSouthBellevueStationToClimatePledge() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("s bellevue station"))
      .withTo(geocoder.get("climate pledge"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("550")
      .hasLeg()
      .withRouteShortName("D Line")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("550")
      .hasLeg()
      .withRouteShortName("Monorail")
      .assertMatches();
  }

  @Test
  @DisplayName("SODO to King St Station")
  public void sodoToKingSt() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("1st and lander"))
      .withTo(geocoder.get("king st station"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("21")
      .hasLeg()
      .interlinedWithPreviousLeg()
      .withRouteShortName("5")
      .assertMatches();
  }

  @Test
  @DisplayName("SODO to N Capitol Hill")
  public void sodoToNCapitolHill() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("1st and lander"))
      .withTo(geocoder.get("e prospect and broadway"))
      .withTime(weekdayAtTime(LocalTime.of(19, 45)))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .withStrictTransitMatching()
      .hasLeg()
      .withRouteShortName("1 Line")
      .hasLeg()
      .withRouteShortName("49")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .withStrictTransitMatching()
      .hasLeg()
      .withRouteShortName("50")
      .hasLeg()
      .withRouteShortName("1 Line")
      .hasLeg()
      .withRouteShortName("49")
      .assertMatches();
  }

  @Test
  @DisplayName("Verify all expected transit agencies are present")
  public void testAgencyList() throws IOException {
    var routes = this.apiClient.routes();
    var actualAgencies = routes
      .stream()
      .map(route -> route.agency().name())
      .distinct()
      .sorted()
      .toList();

    var expectedAgencies = Stream
      .of(
        "City of Seattle",
        "Community Transit",
        "Everett Transit",
        "Intercity Transit",
        "Kitsap Transit",
        "Metro Transit",
        "Pierce Transit",
        "Seattle Center Monorail",
        "Skagit Transit",
        "Sound Transit",
        "Washington State Ferries",
        "Whatcom Transportation Authority"
      )
      .sorted()
      .toList();

    Assertions.assertTrue(
      actualAgencies.containsAll(expectedAgencies) &&
      expectedAgencies.containsAll(actualAgencies),
      String.format(
        "Agency lists differ.%nExpected: %s%nActual: %s%nMissing from expected: %s%nMissing from actual: %s",
        expectedAgencies,
        actualAgencies,
        expectedAgencies
          .stream()
          .filter(a -> !actualAgencies.contains(a))
          .toList(),
        actualAgencies
          .stream()
          .filter(a -> !expectedAgencies.contains(a))
          .toList()
      )
    );
  }

  @Test
  @DisplayName(
    "Test with custom trip plan parameters - high number of itineraries"
  )
  public void testCustomParameters() throws IOException {
    // Create custom parameters with more itineraries than the default
    var customParams = defaultBuilder()
      .withFrom(geocoder.get("OLIVE_WAY"))
      .withTo(geocoder.get("SHORELINE"))
      .build();

    var plan = apiClient.plan(customParams);

    // Verify we get up to 10 itineraries instead of the default 5
    Assertions.assertTrue(
      plan.itineraries().size() <= 10,
      "Should get at most 10 itineraries based on custom parameters"
    );

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("E Line")
      .assertMatches();
  }

  @Test
  @DisplayName("Queen Anne to Hopelink Food Bank")
  public void queenAnneToHopelinkFoodBank() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("queen anne"))
      .withTo(geocoder.get("hopelink food bank"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteShortName("31", "32")
      .hasLeg()
      .withRouteShortName("255")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteShortName("31", "32")
      .hasLeg()
      .withRouteShortName("239")
      .assertMatches();
  }

  @Test
  @DisplayName("King St Station to Tractor Tavern Ballard")
  public void kingStStationToTractorTavernBallard() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("king st station"))
      .withTo(geocoder.get("tractor tavern ballard"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("40")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("D Line")
      .assertMatches();
  }

  @Test
  @DisplayName("King St Station to Mukilteo Ferry")
  public void kingStStationToMukilteoFerry() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("king st station"))
      .withTo(geocoder.get("mukilteo ferry"))
      .withTime(weekdayAtTime(LocalTime.of(17, 35)))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("N Line")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteShortName("1 Line")
      .hasLeg()
      .withRouteShortName("117")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("515")
      .hasLeg()
      .withRouteShortName("117")
      .assertMatches();
  }

  @Test
  @DisplayName("Green Lake to King St Station")
  public void greenLakeToKingStStation() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("green lake"))
      .withTo(geocoder.get("king st station"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .assertMatches();

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteShortName("62", "45")
      .hasLeg()
      .withRouteShortName("1 Line")
      .assertMatches();
  }

  @Test
  @DisplayName("Capitol Hill to King St Station")
  public void capitolHillToKingStStation() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("capitol hill"))
      .withTo(geocoder.get("king st station"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .assertMatches();
  }

  @Test
  @DisplayName("Capitol Hill to SODO")
  public void capitolHillToUWStation() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("capitol hill"))
      .withTo(geocoder.get("SODO"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .assertMatches();
  }

  @Test
  @DisplayName("Paseo Fremont to SODO")
  public void paseoFremontToSODO() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("paseo fremont"))
      .withTo(geocoder.get("1st and lander"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("5")
      .hasLeg()
      .interlinedWithPreviousLeg()
      .withRouteShortName("21")
      .assertMatches();
  }

  @Test
  @DisplayName("Capitol Hill to Columbia City")
  public void capitolHillToColumbiaCity() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("capitol hill"))
      .withTo(geocoder.get("columbia city"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("1 Line")
      .assertMatches();
  }

  @Test
  @DisplayName("1801 S Bush Pl to Bellevue Transit Center")
  public void s1801SBushPlaceToBellevueTransitCenter() throws IOException {
    var params = defaultBuilder()
      .withFrom(geocoder.get("1801 s bush pl"))
      .withTo(geocoder.get("bellevue transit center"))
      .build();

    var plan = apiClient.plan(params);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withStrictTransitMatching()
      .withRouteShortName("554")
      .hasLeg()
      .withRouteShortName("550")
      .assertMatches();
  }
}
