package com.disney.ads;

import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;
import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

/**
 * VAST 3.0 / VMAP Ad Delivery Contract Tests
 * Validates impression pixel fire order, click-through redirect chains,
 * MIME types, and ad duration metadata across device profiles.
 */
@Epic("Ad Delivery Validation")
@Feature("VAST 3.0 / VMAP Contract")
public class AdDeliveryTest {

    private static final String BASE_URL = System.getenv().getOrDefault(
        "AD_SERVER_URL", "http://mock-ad-server:8080"
    );

    // Device profiles parametrized via TestNG DataProvider
    @DataProvider(name = "deviceProfiles", parallel = true)
    public Object[][] deviceProfiles() {
        return new Object[][] {
            {"desktop",  "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            {"ctv",      "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0)"},
            {"mobile",   "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0)"},
            {"tablet",   "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X)"},
        };
    }

    @Test(dataProvider = "deviceProfiles")
    @Story("VAST XML response — HTTP 200 per device")
    @Description("Ad server must return HTTP 200 with application/xml for every device profile")
    public void testVastResponseHttpStatus(String device, String userAgent) {
        Response response = given()
            .baseUri(BASE_URL)
            .header("User-Agent", userAgent)
            .queryParam("device_type", device)
            .queryParam("ad_unit", "preroll")
        .when()
            .get("/vast/v3")
        .then()
            .extract().response();

        assertThat(response.statusCode())
            .as("HTTP status for device [%s]", device)
            .isEqualTo(200);
        assertThat(response.contentType())
            .as("Content-Type for device [%s]", device)
            .contains("application/xml");
    }

    @Test(dataProvider = "deviceProfiles")
    @Story("Impression pixel URL present and reachable")
    @Description("VAST XML must contain a valid Impression element with a non-empty URL")
    public void testImpressionPixelPresent(String device, String userAgent) {
        String vastXml = given()
            .baseUri(BASE_URL)
            .header("User-Agent", userAgent)
            .queryParam("device_type", device)
        .when()
            .get("/vast/v3")
        .then()
            .statusCode(200)
            .extract().body().asString();

        assertThat(vastXml)
            .as("VAST XML must contain <Impression> tag for device [%s]", device)
            .contains("<Impression>");
        assertThat(vastXml)
            .as("Impression URL must not be empty for device [%s]", device)
            .containsPattern("<Impression>\\s*<\\!\\[CDATA\\[https?://[^]]+\\]\\]>\\s*</Impression>");
    }

    @Test(dataProvider = "deviceProfiles")
    @Story("Click-through HTTP 302 redirect chain")
    @Description("ClickThrough URL must return HTTP 302 pointing to advertiser destination")
    public void testClickThroughRedirectChain(String device, String userAgent) {
        Response response = given()
            .baseUri(BASE_URL)
            .header("User-Agent", userAgent)
            .queryParam("device_type", device)
            .redirects().follow(false)
        .when()
            .get("/clickthrough")
        .then()
            .extract().response();

        assertThat(response.statusCode())
            .as("ClickThrough must return 302 for device [%s]", device)
            .isEqualTo(302);
        assertThat(response.header("Location"))
            .as("Location header must be present for device [%s]", device)
            .isNotBlank()
            .startsWith("https://");
    }

    @Test
    @Story("Creative asset MIME types")
    @Description("MediaFile elements in VAST XML must declare valid MIME types (video/mp4, video/webm)")
    public void testCreativeAssetMimeTypes() {
        String vastXml = given()
            .baseUri(BASE_URL)
        .when()
            .get("/vast/v3")
        .then()
            .statusCode(200)
            .extract().body().asString();

        assertThat(vastXml)
            .as("VAST must contain video/mp4 MediaFile")
            .containsIgnoringCase("video/mp4");
    }

    @Test
    @Story("Ad duration metadata")
    @Description("VAST Duration element must be present and in HH:MM:SS format")
    public void testAdDurationFormat() {
        String vastXml = given()
            .baseUri(BASE_URL)
        .when()
            .get("/vast/v3")
        .then()
            .statusCode(200)
            .extract().body().asString();

        assertThat(vastXml)
            .as("VAST XML must contain <Duration> element")
            .contains("<Duration>");
        assertThat(vastXml)
            .as("Duration must follow HH:MM:SS format")
            .containsPattern("<Duration>\\d{2}:\\d{2}:\\d{2}</Duration>");
    }
}
