package app.mock
import org.mockserver.integration._
import org.mockserver.mock.Expectation
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

import scala.util.Random

object MockServerEndpoints {

  def mkEndpoints(mockCS: ClientAndServer): Array[Expectation] = {
    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/index.html")
      )
      .respond(
        response()
          .withBody("<title>Example title</title>")
          .withStatusCode(200)
      )

    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/no-title.html")
      )
      .respond(
        response()
          .withBody("<no-title>")
          .withStatusCode(200)
      )

    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/wrong-data")
      )
      .respond(
        response()
          .withBody(Random.shuffle("wrong data".getBytes()).toArray)
      )

    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/not-found")
      )
      .respond(
        response().withStatusCode(404)
      )

    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/follow-location")
      )
      .respond(
        response()
          .withHeader("Location", "/index.html")
          .withStatusCode(302)
      )

    mockCS
      .when(
        request()
          .withMethod("GET")
          .withPath("/follow-location-bad")
      )
      .respond(
        response()
          .withStatusCode( // No Location header,so an error expected in Worker
            302
          )
      )

  }

}
