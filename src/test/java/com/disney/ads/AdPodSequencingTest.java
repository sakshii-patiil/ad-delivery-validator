package com.disney.ads;

import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;
import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

/**
 * Ad Pod Sequencing + Frequency Capping Tests
 * Validates ad pod slot order, frequency cap enforcement, and
 * p95 latency SLA under concurrent load.
 */
@Epic("Ad Delivery Validation")
@Feature("Ad Pod Sequencing & Frequency Capping")
public class AdPodSequencingTest {

    private static final String BASE_URL = System.getenv().getOrDefault(
        "AD_SERVER_URL", "http://mock-ad-server:8080"
    );
    private static final long SLA_P95_MS = 200L;

    @Test
    @Story("Ad pod slot sequence order")
    @Description("VMAP response must return ad breaks in ascending time-offset order")
    public void testAdPodSlotSequencing() {
        String vmapXml = given()
            .baseUri(BASE_URL)
            .queryParam("content_id", "show_episode_001")
        .when()
            .get("/vmap/v1")
        .then()
            .statusCode(200)
            .extract().body().asString();

        // Extract timeOffset values and assert ascending order
        List<String> offsets = extractTimeOffsets(vmapXml);
        assertThat(offsets)
            .as("Ad pod time offsets must be in ascending order")
            .isSortedAccordingTo((a, b) -> toSeconds(a) - toSeconds(b));
        assertThat(offsets)
            .as("VMAP must define at least 2 ad breaks")
            .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Story("Pre-roll appears before mid-roll")
    @Description("Ad pod at offset=start must precede any mid-roll breakpoint")
    public void testPreRollBeforeMidRoll() {
        String vmapXml = given()
            .baseUri(BASE_URL)
            .queryParam("content_id", "show_episode_001")
        .when()
            .get("/vmap/v1")
        .then()
            .statusCode(200)
            .extract().body().asString();

        int startIndex  = vmapXml.indexOf("timeOffset=\"start\"");
        int midIndex    = vmapXml.indexOf("timeOffset=\"00:15:00\"");

        assertThat(startIndex)
            .as("Pre-roll (timeOffset=start) must appear before mid-roll")
            .isGreaterThanOrEqualTo(0)
            .isLessThan(midIndex);
    }

    @Test
    @Story("Frequency cap — max 3 impressions per user per 24h")
    @Description("Same user must not be served the same ad more than 3 times in 24 hours")
    public void testFrequencyCapEnforcement() {
        String userId = "test-user-fc-001";
        String adId   = "ad-creative-42";
        int    cap    = 3;

        // Exhaust the frequency cap
        for (int i = 0; i < cap; i++) {
            given()
                .baseUri(BASE_URL)
                .header("X-User-Id", userId)
                .queryParam("ad_id", adId)
            .when()
                .post("/impression")
            .then()
                .statusCode(200);
        }

        // Next request must be capped — expect 204 No Content (no ad served)
        Response cappedResponse = given()
            .baseUri(BASE_URL)
            .header("X-User-Id", userId)
            .queryParam("ad_id", adId)
        .when()
            .get("/vast/v3")
        .then()
            .extract().response();

        assertThat(cappedResponse.statusCode())
            .as("Request exceeding frequency cap must return 204 (no ad)")
            .isEqualTo(204);
    }

    @Test
    @Story("Response latency SLA — p95 < 200ms")
    @Description("Ad server must respond within 200ms at p95 across 50 sequential requests")
    public void testResponseLatencySLA() {
        int    requestCount = 50;
        long[] latencies    = new long[requestCount];

        for (int i = 0; i < requestCount; i++) {
            long start = System.currentTimeMillis();
            given()
                .baseUri(BASE_URL)
                .queryParam("ad_unit", "preroll")
            .when()
                .get("/vast/v3")
            .then()
                .statusCode(200);
            latencies[i] = System.currentTimeMillis() - start;
        }

        // Sort and take p95
        java.util.Arrays.sort(latencies);
        long p95 = latencies[(int) (requestCount * 0.95)];

        assertThat(p95)
            .as("p95 response latency must be < %dms, was %dms", SLA_P95_MS, p95)
            .isLessThan(SLA_P95_MS);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<String> extractTimeOffsets(String vmapXml) {
        List<String> offsets = new java.util.ArrayList<>();
        int pos = 0;
        while ((pos = vmapXml.indexOf("timeOffset=\"", pos)) != -1) {
            int start = pos + "timeOffset=\"".length();
            int end   = vmapXml.indexOf("\"", start);
            offsets.add(vmapXml.substring(start, end));
            pos = end;
        }
        return offsets;
    }

    private int toSeconds(String offset) {
        if (offset.equals("start")) return -1;
        if (offset.equals("end"))   return Integer.MAX_VALUE;
        String[] parts = offset.split(":");
        return Integer.parseInt(parts[0]) * 3600
             + Integer.parseInt(parts[1]) * 60
             + Integer.parseInt(parts[2]);
    }
}
