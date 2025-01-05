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
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.model.TripPlan;
import org.opentripplanner.client.parameters.TripPlanParameters;

@Tag("smoke-test")
@Tag("hopelink")
public class HopeLinkSmokeTest {

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
  private static final OtpApiClient apiClient = new OtpApiClient(
    ZoneId.of("America/New_York"),
    OTP_WEB_URL
  );

  private static TripPlan flexPlanRequest(String fromStr, String toStr)
    throws IOException {
    return flexPlanRequest(fromStr, toStr, weekdayAtNoon());
  }

  private static TripPlan flexPlanRequest(
    String fromStr,
    String toStr,
    LocalDateTime time
  ) throws IOException {
    var from = COORDS.get(fromStr);
    var to = COORDS.get(toStr);
    return apiClient.plan(
      TripPlanParameters
        .builder()
        .withModes(FLEX_DIRECT_MODES)
        .withNumberOfItineraries(20)
        .withFrom(from)
        .withTo(to)
        .withTime(time)
        .withSearchDirection(TripPlanParameters.SearchDirection.DEPART_AT)
        .build()
    );
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
    COORDS.add("ArlingtonLib", 48.2546724, -121.6037822);
    COORDS.add("DarringtonLib", 48.19418487200602, -122.11738797581259);
    COORDS.add("KenmorePR", 47.759201, -122.243057);
    COORDS.add("MountlakeTerraceTC", 47.785057, -122.314788);
  }

  private static final Set<RequestMode> FLEX_DIRECT_MODES = Set.of(
    FLEX_DIRECT,
    FLEX_EGRESS,
    FLEX_ACCESS,
    TRANSIT,
    WALK
  );

  @Test
  public void insideTacoma() throws IOException {
    // First trip plan
    var request1 = new SmokeTestRequest(
      COORDS.get("Tacoma"),
      COORDS.get("Puyallup"),
      FLEX_DIRECT_MODES,
      false,
      apiClient
    );
    var plan = SmokeTestRequest.basicTripPlan(request1);

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
  public void snoqualmieToNBend() throws IOException {
    var plan = flexPlanRequest("Snoqualmie", "NBend");
    listLongNames(plan);
    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Services: King County");
    checkLongName(plan, "Door-To-Door");
    checkLongName(plan, "Medicaid Transportation");
  }

  @Test
  public void insideBellevue() throws IOException {
    var plan = flexPlanRequest("Bellevue", "Bellevue2");

    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Hyde Shuttle");
    checkLongName(plan, "Volunteer Services: King County");
    checkLongName(plan, "Medicaid Transportation");
  }

  @Test
  public void marysvilleToEverett() throws IOException {
    var plan = flexPlanRequest("Marysville", "Everett");
    listLongNames(plan);

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Paratransit");
  }

  @Test
  public void withinLynnwood() throws IOException {
    var plan = flexPlanRequest("Lynnwood", "Lynnwood2");

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Alderwood Shuttle");
  }

  @Test
  public void withinBothell() throws IOException {
    var plan = flexPlanRequest("Bothell", "Bothell2");
    listLongNames(plan);

    checkLongName(plan, "Road to Recovery");
    checkLongName(plan, "Volunteer Transportation");
    checkLongName(plan, "Medicaid Transportation");
    checkLongName(plan, "Transportation Service");
    checkLongName(plan, "Volunteer Services: King County");
  }

  @Test
  public void arlingtonToDarrington() throws IOException {
    var plan = flexPlanRequest(
      "ArlingtonLib",
      "DarringtonLib",
      weekdayAtTime(LocalTime.of(8, 0))
    );

    listLongNames(plan);
    // only getting this one on QA?
    checkLongName(plan, "D'Arling Direct");
  }

  @Test
  public void metroFlexNorth() throws IOException {
    var plan = flexPlanRequest("KenmorePR", "MountlakeTerraceTC");

    listLongNames(plan);
    checkLongName(plan, "Northshore");
  }
}
