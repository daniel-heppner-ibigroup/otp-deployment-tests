package com.arcadis.otpsmoketests.tests;

import static com.arcadis.otpsmoketests.itineraryassertations.SmokeTestRequest.weekdayAtNoon;
import static com.arcadis.otpsmoketests.itineraryassertations.SmokeTestRequest.weekdayAtTime;
import static org.opentripplanner.client.model.RequestMode.FLEX_ACCESS;
import static org.opentripplanner.client.model.RequestMode.FLEX_DIRECT;
import static org.opentripplanner.client.model.RequestMode.FLEX_EGRESS;
import static org.opentripplanner.client.model.RequestMode.TRANSIT;
import static org.opentripplanner.client.model.RequestMode.WALK;

import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestItinerary;
import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;
import com.arcadis.otpsmoketests.BaseTestSuite;

@Tag("smoke-test")
@Tag("hopelink")
@DisplayName("Hopelink Smoke Tests")
public class HopeLinkTestSuite extends BaseTestSuite {

  public HopeLinkTestSuite() {
    super("Hopelink", "https://hopelink-otp.ibi-transit.com");
  }

  @Override
  protected void initializeCoordinates() {
    geocoder.storeCoordinate("Tacoma", 47.253304, -122.445237);
    geocoder.storeCoordinate("Puyallup", 47.189659, -122.295414);
    geocoder.storeCoordinate("Tukwila", 47.474005, -122.284023);
    geocoder.storeCoordinate("Bellevue", 47.620659, -122.187675);
    geocoder.storeCoordinate("Snoqualmie", 47.512752, -121.885709);
    geocoder.storeCoordinate("NBend", 47.492737, -121.788474);
    geocoder.storeCoordinate("Bellevue2", 47.571076, -122.163592);
    geocoder.storeCoordinate("Marysville", 48.060692, -122.174907);
    geocoder.storeCoordinate("Everett", 47.999847, -122.205627);
    geocoder.storeCoordinate("Lynnwood", 47.821271, -122.284036);
    geocoder.storeCoordinate("Lynnwood2", 47.847904, -122.272449);
    geocoder.storeCoordinate("Bothell", 47.780336, -122.211153);
    geocoder.storeCoordinate("Bothell2", 47.758795, -122.194675);
    geocoder.storeCoordinate("ArlingtonLib", 48.193364, -122.118405);
    geocoder.storeCoordinate("DarringtonLib", 48.2546724, -121.6037822);
    geocoder.storeCoordinate("KenmorePR", 47.759201, -122.243057);
    geocoder.storeCoordinate("MountlakeTerraceTC", 47.785057, -122.314788);
    geocoder.storeCoordinate("TukwilaStn", 47.4642067, -122.288452);
    geocoder.storeCoordinate("Burien", 47.474748, -122.283666);
    geocoder.storeCoordinate("FrontierMiddleSchool", 47.055521, -122.289541);
    geocoder.storeCoordinate("OrtingMiddleSchool", 47.101952, -122.217616);
    geocoder.storeCoordinate(
      "PtDefianceTerminal",
      47.305630422593595,
      -122.51442106465043
    );
    try {
      geocoder.geocodeAndStore(
        "ShorelineNStation",
        "Shoreline North/185th Station",
        "openstreetmap"
      );
      geocoder.geocodeAndStore(
        "TargetAlderwood",
        "Target, Alderwood Mall Pkwy, Lynnwood",
        "openstreetmap"
      );
      geocoder.geocodeAndStore(
        "StadiumHS",
        "Stadium High School",
        "openstreetmap"
      );
      geocoder.geocodeAndStore("PDZoo", "Point Defiance Zoo", "openstreetmap");
      geocoder.geocodeAndStore(
        "SumnerSounder",
        "Sumner Sounder Station",
        "openstreetmap"
      );
      geocoder.geocodeAndStore(
        "SumnerSeniorCenter",
        "Sumner Senior Center",
        "openstreetmap"
      );
      geocoder.geocodeAndStore(
          "Seatac",
          "Seatac",
          "otp"
      );
      geocoder.geocodeAndStore(
          "ThrondykeElementary",
          "Thorndyke Elementary",
          "openstreetmap"
      );
      geocoder.geocodeAndStore(
          "MarysvilleAshPR",
          "Marysville Ash Ave Park & Ride (Community Transit)",
          "otp"
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Set<RequestMode> FLEX_DIRECT_MODES = Set.of(
    FLEX_DIRECT,
    FLEX_EGRESS,
    FLEX_ACCESS,
    TRANSIT,
    WALK
  );

  private TripPlan flexPlanRequest(
    String fromStr,
    String toStr,
    LocalDateTime time,
    String testName
  ) throws IOException {
    var from = COORDS.get(fromStr);
    var to = COORDS.get(toStr);

    return this.apiClient.timedPlan(
      TripPlanParameters
        .builder()
        .withModes(FLEX_DIRECT_MODES)
        .withNumberOfItineraries(20)
        .withFrom(from)
        .withTo(to)
        .withTime(time)
        .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
        .build(),
      testName
    );
  }

  private TripPlan flexPlanRequest(
    String fromStr,
    String toStr,
    String testName
  ) throws IOException {
    return flexPlanRequest(fromStr, toStr, weekdayAtNoon(), testName);
  }

  private static void checkLongName(TripPlan plan, String longName) {
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName(longName)
      .assertMatches();
  }

  private static void listLongNames(TripPlan plan) {
    for (var itin : plan
      .itineraries()
      .stream()
      .flatMap(itin -> itin.legs().stream())
      .toList()) {
      if (itin.route() != null && itin.route().longName().isPresent()) {
        System.out.println(itin.route().longName().get());
      }
    }
  }

  @Test
  @DisplayName("Test Road to Recovery and Volunteer Services in Tacoma area")
  public void insideTacoma() throws IOException {
    var plan = flexPlanRequest("Tacoma", "Puyallup", "insideTacoma");

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("Road to Recovery")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("Volunteer Services: Southwest")
      .assertMatches();
  }

  @Test
  @DisplayName("Test transportation services from Burien to Bellevue")
  public void burienToBellevue() throws IOException {
    var request = new SmokeTestRequest(
      COORDS.get("Tukwila"),
      COORDS.get("Bellevue"),
      FLEX_DIRECT_MODES,
      false,
      this.apiClient
    );
    var plan = SmokeTestRequest.basicTripPlan(request);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("Volunteer Transportation")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("Road to Recovery")
      .assertMatches();
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("Medicaid Transportation")
      .assertMatches();
  }

  @Test
  @DisplayName("Test services between Snoqualmie and North Bend")
  public void snoqualmieToNBend() throws IOException {
    var plan = flexPlanRequest("Snoqualmie", "NBend", "snoqualmieToNBend");
    listLongNames(plan);
    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: King County");
    checkLongName(plan, "Door-To-Door");
    checkLongName(plan, "Medicaid Transportation");
  }

  @Test
  @DisplayName("Test local services within Bellevue")
  public void insideBellevue() throws IOException {
    var plan = flexPlanRequest("Bellevue", "Bellevue2", "insideBellevue");

    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Hyde Shuttle");
    checkLongName(plan, "Volunteer Services: King County");
    checkLongName(plan, "Medicaid Transportation");
  }

  @Test
  @DisplayName("Test services from Marysville to Everett")
  public void marysvilleToEverett() throws IOException {
    var plan = flexPlanRequest("MarysvilleAshPR", "Everett", "marysvilleToEverett");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Paratransit");
  }

  @Test
  @DisplayName("Test Alderwood Shuttle within Lynnwood")
  public void withinLynnwood() throws IOException {
    var plan = flexPlanRequest("Lynnwood", "Lynnwood2", "withinLynnwood");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Zip Shuttle");
  }

  @Test
  @DisplayName("Test local services within Bothell")
  public void withinBothell() throws IOException {
    var plan = flexPlanRequest("Bothell2", "Bothell", "withinBothell");

    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Transportation Service");
    checkLongName(plan, "Volunteer Services: King County");
    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Road to Recovery");
  }

  @Test
  @DisplayName("Test D'Arling Direct service from Arlington to Darrington")
  public void arlingtonToDarrington() throws IOException {
    var plan = flexPlanRequest(
      "ArlingtonLib",
      "DarringtonLib",
      weekdayAtTime(LocalTime.of(7, 50)),
      "arlingtonToDarrington"
    );

    checkLongName(plan, "D'Arling Direct");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");

    plan =
      this.apiClient.plan(
        TripPlanParameters
          .builder()
          .withModes(Set.of(TRANSIT, WALK))
          .withNumberOfItineraries(20)
          .withFrom(COORDS.get("ArlingtonLib"))
          .withTo(COORDS.get("DarringtonLib"))
          .withTime(weekdayAtTime(LocalTime.of(7, 49)))
          .withTime(LocalDateTime.of(2025, 1, 15, 7, 49))
          .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
          .build()
      );

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteLongName("D'Arling Direct")
      .hasLeg()
      .withRouteLongName("DC Direct")
      .assertMatches();
  }

  @Test
  @DisplayName("Test Metro Flex service in North Seattle")
  public void metroFlexNorth() throws IOException {
    var plan = flexPlanRequest(
      "KenmorePR",
      "MountlakeTerraceTC",
      "metroFlexNorth"
    );

    checkLongName(plan, "Northshore");
  }

  @Test
  @DisplayName("Test Tukwila Flex service")
  public void tukwilaFlex() throws IOException {
    var plan = flexPlanRequest("Burien", "TukwilaStn", "tukwilaFlex");

    checkLongName(plan, "Tukwila");
    checkLongName(plan, "Hyde Shuttle");
    checkLongName(plan, "Medicaid Transportation");
  }

  @Test
  @DisplayName("Test Beyond the Borders service from Graham to Orting")
  public void grahamToOrting() throws IOException {
    var plan = flexPlanRequest(
      "FrontierMiddleSchool",
      "OrtingMiddleSchool",
      "grahamToOrting"
    );

    checkLongName(plan, "Beyond the Borders");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: Southwest");
  }

  @Test
  @DisplayName("StadiumHS To PDZoo")
  public void StadiumHSToPDZoo() throws IOException {
    var plan = flexPlanRequest("StadiumHS", "PDZoo", "StadiumHSToPDZoo");

    checkLongName(plan, "Volunteer Services: Southwest");
    checkLongName(plan, "Road to Recovery");
  }

  @Test
  @DisplayName("Sumner Sounder to Senior Center")
  public void SumnerSounderToSeniorCenter() throws IOException {
    var plan = flexPlanRequest(
        "SumnerSounder",
        "SumnerSeniorCenter",
        weekdayAtTime(LocalTime.of(14, 40)),
        "SumnerSounderToSeniorCenter"
    );
    checkLongName(plan, "Beyond the Borders");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: Southwest");
    checkLongName(plan, "Sumner-Bonney Lake Connector");
  }

  @Test
  @DisplayName("Test Ruston Runner service")
  public void rustonRunner() throws IOException {
    var plan = flexPlanRequest(
      "StadiumHS",
      "PtDefianceTerminal",
      "rustonRunner"
    );

    checkLongName(plan, "Runner");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: Southwest");
  }

  @Test
  @DisplayName("1 Line to Zip")
  public void LinkToZip() throws IOException {
    var plan = flexPlanRequest(
      "ShorelineNStation",
      "Lynnwood2",
      "1lineToZip"
    );
    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withRouteShortName("1 Line")
      .hasLeg()
      .withRouteLongName("Zip Shuttle")
      .assertMatches();
  }

  @Test
  @DisplayName("1 Line to Metro Flex Tukwila")
  public void LinkToMetroFlex() throws IOException {
    var plan = flexPlanRequest(
        "Seatac",
        "ThrondykeElementary",
        "LinkToMetroFlex"
    );

    SmokeTestItinerary
        .from(plan)
        .hasLeg()
        .withRouteShortName("1 Line")
        .hasLeg()
        .withRouteLongName("Tukwila")
        .assertMatches();
  }
}
