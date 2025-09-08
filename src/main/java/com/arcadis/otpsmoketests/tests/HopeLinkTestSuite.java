package com.arcadis.otpsmoketests.tests;

import static org.opentripplanner.client.model.RequestMode.FLEX_ACCESS;
import static org.opentripplanner.client.model.RequestMode.FLEX_DIRECT;
import static org.opentripplanner.client.model.RequestMode.FLEX_EGRESS;
import static org.opentripplanner.client.model.RequestMode.TRANSIT;
import static org.opentripplanner.client.model.RequestMode.WALK;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.itineraryassertations.SmokeTestItinerary;
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

@Tag("smoke-test")
@Tag("hopelink")
@DisplayName("Hopelink Smoke Tests")
public class HopeLinkTestSuite extends BaseTestSuite {

  public HopeLinkTestSuite() {
    this("https://hopelink-otp.ibi-transit.com", "Hopelink");
  }

  public HopeLinkTestSuite(String baseUrl) {
    this(baseUrl, "Hopelink");
  }

  public HopeLinkTestSuite(String baseUrl, String deploymentName) {
    super(
      deploymentName,
      baseUrl,
      "https://im5b1wfh6d.execute-api.us-east-1.amazonaws.com/commtrans/autocomplete",
      47.61097,
      -122.33701
    );
  }

  @Override
  protected void initializeCoordinates() {
    geocoder.add("Tacoma", 47.253304, -122.445237);
    geocoder.add("Puyallup", 47.189659, -122.295414);
    geocoder.add("Tukwila", 47.474005, -122.284023);
    geocoder.add("Bellevue", 47.620659, -122.187675);
    geocoder.add("Snoqualmie", 47.512752, -121.885709);
    geocoder.add("NBend", 47.492737, -121.788474);
    geocoder.add("Bellevue2", 47.571076, -122.163592);
    geocoder.add("Marysville", 48.060692, -122.174907);
    geocoder.add("Everett", 47.999847, -122.205627);
    geocoder.add("Lynnwood", 47.821271, -122.284036);
    geocoder.add("Lynnwood2", 47.847904, -122.272449);
    geocoder.add("Bothell", 47.780336, -122.211153);
    geocoder.add("Bothell2", 47.758795, -122.194675);
    geocoder.add("ArlingtonLib", 48.193364, -122.118405);
    geocoder.add("DarringtonLib", 48.2546724, -121.6037822);
    geocoder.add("KenmorePR", 47.759201, -122.243057);
    geocoder.add("MountlakeTerraceTC", 47.785057, -122.314788);
    geocoder.add("TukwilaStn", 47.4642067, -122.288452);
    geocoder.add("Burien", 47.474748, -122.283666);
    geocoder.add("FrontierMiddleSchool", 47.055521, -122.289541);
    geocoder.add("OrtingMiddleSchool", 47.101952, -122.217616);
    geocoder.add("PtDefianceTerminal", 47.305630422593595, -122.51442106465043);
    try {
      geocoder.addGeocoded(
        "ShorelineNStation",
        "Shoreline North/185th Station",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "TargetAlderwood",
        "Target, Alderwood Mall Pkwy, Lynnwood",
        "openstreetmap"
      );
      geocoder.addGeocoded("StadiumHS", "Stadium High School", "openstreetmap");
      geocoder.addGeocoded("PDZoo", "Point Defiance Zoo", "openstreetmap");
      geocoder.addGeocoded(
        "SumnerSounder",
        "Sumner Sounder Station",
        "openstreetmap"
      );
      geocoder.addGeocoded(
        "SumnerSeniorCenter",
        "Sumner Senior Center",
        "openstreetmap"
      );
      geocoder.addGeocoded("Seatac", "Seatac", "otp");
      geocoder.addGeocoded(
        "ThrondykeElementary",
        "Thorndyke Elementary",
        "openstreetmap"
      );
      geocoder.addGeocoded(
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
    var from = geocoder.get(fromStr);
    var to = geocoder.get(toStr);

    return this.apiClient.timedPlan(
        defaultBuilder()
          .withModes(FLEX_DIRECT_MODES)
          .withFrom(from)
          .withTo(to)
          .withTime(time)
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
    var params = TripPlanParameters
      .builder()
      .withFrom(geocoder.get("Tukwila"))
      .withTo(geocoder.get("Bellevue"))
      .withModes(FLEX_DIRECT_MODES)
      .withTime(weekdayAtNoon())
      .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
      .build();

    var plan = apiClient.plan(params);

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
    var plan = flexPlanRequest(
      "MarysvilleAshPR",
      "Everett",
      "marysvilleToEverett"
    );

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
            .withFrom(geocoder.get("ArlingtonLib"))
            .withTo(geocoder.get("DarringtonLib"))
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
    var plan = flexPlanRequest("ShorelineNStation", "Lynnwood2", "1lineToZip");
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
