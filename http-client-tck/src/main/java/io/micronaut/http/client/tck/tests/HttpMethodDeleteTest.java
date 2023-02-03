/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension",
})
class HttpMethodDeleteTest {

    private static final String SPEC_NAME = "HttpMethodDeleteTest";

    @Test
    void deleteMethodMapping() throws IOException {
        asserts(SPEC_NAME, HttpRequest.DELETE("/delete"), (server, request) ->
            AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .build())
        );
    }

    @Test
    void deleteMethodClientMappingWithStringResponse() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            HttpMethodDeleteClient client = server.getApplicationContext().getBean(HttpMethodDeleteClient.class);
            assertEquals("ok", client.response());
        }
    }

    @Test
    void deleteMethodMappingWithStringResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.DELETE("/delete/string-response"),
            (server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body("ok")
                        .build())
        );
    }

    @Test
    void deleteMethodMappingWithObjectResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.DELETE("/delete/object-response"),
            (server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request,
                    HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("{\"name\":\"Tim\",\"age\":49}").equals())
                        .build())
        );
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/delete")
    static class HttpMethodDeleteTestController {

        @Delete
        @Status(HttpStatus.NO_CONTENT)
        void index() {
            // no-op
        }

        @Delete("/string-response")
        String response() {
            return "ok";
        }

        @Delete("/object-response")
        Person person() {
            return new Person("Tim", 49);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Client("/delete")
    interface HttpMethodDeleteClient {

        HttpResponse<Void> index();

        @Delete("/string-response")
        String response();

        @Delete("/object-response")
        Person person();
    }
}
