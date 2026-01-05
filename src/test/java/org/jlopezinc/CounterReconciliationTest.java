package org.jlopezinc;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@QuarkusTest
public class CounterReconciliationTest {

    private static final String API_KEY = "7KVjU7bQmy";
    private static final String TEST_EVENT = "test-reconcile-event";

    @Test
    public void testReconcileCountersRequiresAuth() {
        // Call without API key should fail
        RestAssured.given()
                .when()
                .post("/v1/admin/reconcile-counters/" + TEST_EVENT)
                .then()
                .statusCode(401);

        // Call with wrong API key should fail
        RestAssured.given()
                .header("x-api-key", "wrong-key")
                .when()
                .post("/v1/admin/reconcile-counters/" + TEST_EVENT)
                .then()
                .statusCode(401);
    }

    @Test
    public void testReconcileCountersEndpointExists() {
        // This test verifies the endpoint exists and accepts the correct API key
        // It will fail at the database level due to lack of AWS credentials in test env
        // but that's expected - we're just testing that the endpoint is wired correctly
        RestAssured.given()
                .header("x-api-key", API_KEY)
                .when()
                .post("/v1/admin/reconcile-counters/" + TEST_EVENT)
                .then()
                .statusCode(is(500)); // Expected: 500 due to no DB in test env, not 404
    }
}
