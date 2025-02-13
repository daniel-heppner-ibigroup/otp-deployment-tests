package com.arcadis.otp_deployment_tests.tests;

import static com.arcadis.otp_deployment_tests.SmokeTestRequest.weekdayAtNoon;
import static com.arcadis.otp_deployment_tests.SmokeTestRequest.weekdayAtTime;
import static org.opentripplanner.client.model.RequestMode.FLEX_ACCESS;
import static org.opentripplanner.client.model.RequestMode.FLEX_DIRECT;
import static org.opentripplanner.client.model.RequestMode.FLEX_EGRESS;
import static org.opentripplanner.client.model.RequestMode.TRANSIT;
import static org.opentripplanner.client.model.RequestMode.WALK;

import com.arcadis.otp_deployment_tests.CoordinatesStore;
import com.arcadis.otp_deployment_tests.SmokeTestItinerary;
import com.arcadis.otp_deployment_tests.SmokeTestRequest;
import com.arcadis.otp_deployment_tests.TimedOtpApiClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

@Tag("smoke-test")
@Tag("hopelink")
@DisplayName("Hopelink Smoke Tests")
public class HopeLinkSmokeTest {

  private static final MeterRegistry meterRegistry = Metrics.globalRegistry;
  private static final String SUITE_NAME = "Hopelink";

  // Create a timer for each test method
  private static Timer getTestTimer(String testName) {
    return Timer
      .builder("otp.plan.requests." + SUITE_NAME + "." + testName + ".duration")
      .description(
        "Time taken for OTP to respond to plan requests in " + testName
      )
      .tag("service", "hopelink")
      .tag("test", testName)
      .register(meterRegistry);
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

  private static final String OTP_WEB_URL =
    "https://hopelink-otp.ibi-transit.com";
  public static final CoordinatesStore COORDS;
  private static final TimedOtpApiClient apiClient = new TimedOtpApiClient(
    ZoneId.of("America/New_York"),
    OTP_WEB_URL,
    Metrics.globalRegistry,
    SUITE_NAME
  );

  private static TripPlan flexPlanRequest(
    String fromStr,
    String toStr,
    LocalDateTime time,
    String testName
  ) throws IOException {
    var from = COORDS.get(fromStr);
    var to = COORDS.get(toStr);

    return apiClient.timedPlan(
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

  private static TripPlan flexPlanRequest(
    String fromStr,
    String toStr,
    String testName
  ) throws IOException {
    return flexPlanRequest(fromStr, toStr, weekdayAtNoon(), testName);
  }

  static {
    COORDS = new CoordinatesStore();

    COORDS.add("Tacoma", 47.253304, -122.445237);
    COORDS.add("Puyallup", 47.189659, -122.295414);
    COORDS.add("Tukwila", 47.474005, -122.284023);
    COORDS.add("Bellevue", 47.620659, -122.187675);
    COORDS.add("Snoqualmie", 47.512752, -121.885709);
    COORDS.add("NBend", 47.492737, -121.788474);
    COORDS.add("Bellevue2", 47.571076, -122.163592);
    COORDS.add("Marysville", 48.060692, -122.174907);
    COORDS.add("Everett", 47.999847, -122.205627);
    COORDS.add("Lynnwood", 47.821271, -122.284036);
    COORDS.add("Lynnwood2", 47.847904, -122.272449);
    COORDS.add("Bothell", 47.780336, -122.211153);
    COORDS.add("Bothell2", 47.758795, -122.194675);
    COORDS.add("ArlingtonLib", 48.193364, -122.118405);
    COORDS.add("DarringtonLib", 48.2546724, -121.6037822);
    COORDS.add("KenmorePR", 47.759201, -122.243057);
    COORDS.add("MountlakeTerraceTC", 47.785057, -122.314788);
    COORDS.add("TukwilaStn", 47.4642067, -122.288452);
    COORDS.add("Burien", 47.474748, -122.283666);
    COORDS.add("FrontierMiddleSchool", 47.055521, -122.289541);
    COORDS.add("OrtingMiddleSchool", 47.101952, -122.217616);
    COORDS.add("StadiumHighSchool", 47.266575, -122.449147);
    COORDS.add("PtDefianceTerminal", 47.305630422593595, -122.51442106465043);
  }

  private static final Set<RequestMode> FLEX_DIRECT_MODES = Set.of(
    FLEX_DIRECT,
    FLEX_EGRESS,
    FLEX_ACCESS,
    TRANSIT,
    WALK
  );

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
      apiClient
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
    var plan = flexPlanRequest("Marysville", "Everett", "marysvilleToEverett");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    //    checkLongName(plan, "Paratransit");
  }

  @Test
  @DisplayName("Test Alderwood Shuttle within Lynnwood")
  public void withinLynnwood() throws IOException {
    var plan = flexPlanRequest("Lynnwood", "Lynnwood2", "withinLynnwood");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Alderwood Shuttle");
  }

  @Test
  @DisplayName("Test local services within Bothell")
  public void withinBothell() throws IOException {
    var plan = flexPlanRequest("Bothell", "Bothell2", "withinBothell");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Transportation Service");
    checkLongName(plan, "Volunteer Services: King County");
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
      apiClient.plan(
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
  @DisplayName("Test Ruston Runner service")
  public void rustonRunner() throws IOException {
    var plan = flexPlanRequest(
      "StadiumHighSchool",
      "PtDefianceTerminal",
      "rustonRunner"
    );

    checkLongName(plan, "Runner");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: Southwest");
  }
}
