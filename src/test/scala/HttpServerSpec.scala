package app

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import doobie.util.transactor.Transactor
import org.http4s.{Header, HttpApp, Method, Request, Response, Status, Uri}
import org.http4s.client.Client

import app.env.{Config, Env, DbConfig}

class HttpServerSpec extends org.specs2.mutable.Specification {

  "HttpServer" >> {

    "GetDevices" >> {

      "return 401" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/devices")))
        response.status must beEqualTo(Status.Unauthorized)
        response.headers.get(org.http4s.headers.`WWW-Authenticate`) must beEqualTo(Some(Header("WWW-Authenticate", "Bearer realm=\"Protected Realm\"")))
      }

      "return 200" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/repos"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.status must beEqualTo(Status.Ok)
      }

      "return devices" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/repos"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.as[String].unsafeRunSync() must beEqualTo("""[{"repo":"repo1","lastModified":"1970-01-01T00:00:00Z","lastUpdated":"1970-01-01T00:00:00Z","children":[{"uri":"slug1.tar.gz","type":"SLUG"}]}]""")
      }
    }

    "GetHome" >> {

      "return 401" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/homes/1")))
        response.status must beEqualTo(Status.Unauthorized)
        response.headers.get(org.http4s.headers.`WWW-Authenticate`) must beEqualTo(Some(Header("WWW-Authenticate", "Bearer realm=\"Protected Realm\"")))
      }

      "return 200" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/homes/1"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.status must beEqualTo(Status.Ok)
      }

      "return home" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/homes/1"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.as[String].unsafeRunSync() must beEqualTo("""{"id":175482,"address":"12 Newbury","postcode":"RG14 2PZ"}""")
      }

      "return 404" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/homes/2"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.status must beEqualTo(Status.NotFound)
      }

      "return no home" >> {
        val response = responseFor(Request[IO](Method.GET, Uri.uri("/v1/homes/2"))
                                    .putHeaders(Header("Authorization", "Bearer accessToken")))
        response.as[String].unsafeRunSync() must beEqualTo("")
      }
    }
  }

  private[this] val httpApp: HttpApp[IO] = {
    import scala.concurrent.ExecutionContext.global
    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    implicit val timer: Timer[IO] = IO.timer(global)

    val client = {
      import org.http4s.dsl.io._
      Client.fromHttpApp(HttpApp[IO] {
        case GET -> Root / "sso" / "v" / "1" / "user" ⇒
          Ok("""{"username": "consumer_user"}""")
        case GET -> Root / "artifactory" / "v" / "1" / "repos" ⇒
          Ok("""[{
            "repo"        : "repo1",
            "lastModified": "1970-01-01T00:00:00Z",
            "lastUpdated" : "1970-01-01T00:00:00Z",
            "children"    : [{
              "uri"     : "slug1.tar.gz",
              "isFolder": false
            }]
          }]""")
        case GET -> Root / "homes" / "1" ⇒
          Ok("""{
            "id"      : "175482",
            "address" : "12 Newbury",
            "postcode": "RG14 2PZ"
          }""")
        case GET -> Root / "homes" / i ⇒
          NotFound(s"Home $i not found")
        case r ⇒
          NotFound(s"No mock for $r")
      })
    }

    val count = Ref.of[IO, Int](0).unsafeRunSync()

    val env = Env( config = Config("""{ logger.application = DEBUG
                                      , artifactory.uri = "http://localhost:10001"
                                      , home.uri        = "http://localhost:10002"
                                      , sso.uri         = "http://localhost:10003"
                                      }""")
                 , client = client
                 , count  = count
                 )


    // TODO mock db for tests
    def xa(dbConfig: DbConfig): Transactor[IO] =
      Transactor.fromDriverManager[IO](
          driver = dbConfig.driver
        , url    = dbConfig.url
        , user   = dbConfig.username
        , pass   = dbConfig.password
        )

    val dbConfig = DbConfig(
        driver   = "org.h2.Driver"
      , url      = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
      , username = "sa"
      , password = ""
      )

    HttpServer.httpApp(env, xa(dbConfig))
  }

  private[this] def responseFor(req: Request[IO]): Response[IO] =
    httpApp(req).unsafeRunSync()
}
