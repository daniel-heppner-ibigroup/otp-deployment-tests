package com.arcadis.otp_deployment_tests.tests;

import com.arcadis.otp_deployment_tests.CoordinatesStore;
import com.arcadis.otp_deployment_tests.SmokeTestItinerary;
import com.arcadis.otp_deployment_tests.SmokeTestRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.OtpApiClient;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Set;

import static org.opentripplanner.client.model.RequestMode.FLEX_DIRECT;
import static org.opentripplanner.client.model.RequestMode.WALK;

@Tag("smoke-test")
@Tag("hopelink")
public class HopeLinkSmokeTest {
    private static final String OTP_WEB_URL = "https://hopelink-otp.ibi-transit.com";
    public static final CoordinatesStore COORDS;
    private static final OtpApiClient apiClient = new OtpApiClient(ZoneId.of("America/New_York"), OTP_WEB_URL);

    static {
        COORDS = new CoordinatesStore();

        COORDS.add("930 Tacoma Ave", 47.253304, -122.445237 );
        COORDS.add("324 S Meridian", 47.189659, -122.295414);
    }

    @Test
    public void volunteerServicesSw() throws IOException {
        var modes = Set.of(FLEX_DIRECT, WALK);

        // First trip plan
        var request1 = new SmokeTestRequest(COORDS.get("930 Tacoma Ave"), COORDS.get("324 S Meridian"), modes, false, apiClient);
        var plan = SmokeTestRequest.basicTripPlan(request1);

        SmokeTestItinerary.from(plan)
            .hasLeg()
            .withRouteLongName("Road to Recovery")
            .assertMatches();
        SmokeTestItinerary.from(plan)
            .hasLeg()
            .withRouteLongName("Volunteer Services: Southwest")
            .assertMatches();
    }
} 