package io.micronaut.http.server.netty.binding

import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

class DefaultJsonErrorHandlingSpec extends AbstractMicronautSpec {

    void "test map-based body parsing with invalid JSON"() {

        when:
        def json = '{"title":"The Stand"'
        Flux.from(rxClient.exchange(
                HttpRequest.POST('/errors/map', json), String
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == """Invalid JSON: Unexpected end-of-input: expected close marker for Object (start marker at [Source: (byte[])"{"title":"The Stand""; line: 1, column: 1])
 at [Source: (byte[])"{"title":"The Stand""; line: 1, column: 21]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        String body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        result['_links'].self.href == '/errors/map'
        result['_embedded'].errors[0].message.startsWith('Invalid JSON')
    }

    @Controller("/errors")
    static class ErrorsController {
        @Post("/string")
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post("/map")
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }
    }
}
