package app

import cats.effect.{ExitCode, IO, IOApp, Ref, Sync}
import cats.effect.kernel.Async
import cats.mtl.Ask
import doobie.util.transactor.Transactor
import fs2.Stream
import org.http4s.{AuthedRoutes, HttpApp, HttpRoutes}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, HttpMiddleware, Router}
import org.http4s.server.middleware.{AutoSlash, GZip}
import org.log4s.getLogger
import scala.concurrent.ExecutionContext.Implicits.global

import app.auth.OAuth
import app.endpoint.{RepoEndpoint, HomeEndpoint}
import app.env.{Config, Env, DbConfig, ReqEnv}
import app.model.HomeId
import app.service.{ApiVersion, ArtifactoryService, DbService, HomeService, SsoService}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    HttpServer.stream[IO].compile.drain.as(ExitCode.Success)
}


object HttpServer {

  private[this] val logger = getLogger

  def stream[F[_] : Async](implicit F: Sync[F]): Stream[F, ExitCode] =
    for {
      client   <- BlazeClientBuilder[F](global).stream
      count    <- Stream.eval(Ref.of(0))
      port     <- Stream.eval(F.delay(sys.props.getOrElse("http.port", "8080").toInt))
      _        <- Stream.eval(F.delay(logger.info(s"Starting server on $port")))
      config   =  Config()
      env      =  Env( config = config
                     , client = client
                     , count = count
                     )
      exitCode <- Stream.resource(DbConfig.transactor(config.ds)).flatMap { xa =>
                    BlazeServerBuilder[F](global)
                      .bindHttp(port)
                      .withHttpApp(httpApp(env, xa))
                      .serve
                  }
    } yield exitCode

  def httpApp[F[_] : Async](env: Env[F], xa: Transactor[F]): HttpApp[F] = {

    implicit val ea = Ask.const(env)

    val artifactoryService = new ArtifactoryService[F]
    val dbService          = new DbService[F](xa)
    val homeService        = new HomeService[F]
    val ssoService         = new SsoService[F]

    val middleware: HttpMiddleware[F] =
                { routes: HttpRoutes[F] => GZip(routes)      }
       .compose { routes: HttpRoutes[F] => AutoSlash(routes) }

    val authMiddleware: AuthMiddleware[F, OAuth.User] = OAuth[F](ssoService)

    val repoEndpoint = new RepoEndpoint(artifactoryService)
    val homeEndpoint = new HomeEndpoint(homeService, dbService)

    implicit def reqEnvImpl(implicit user: OAuth.User) =
      Ask.const(
        ReqEnv(
          requestId = java.util.UUID.randomUUID
        , user      = user
        )
      )

    val HD = Http4sDsl[F]; import HD._

    import org.http4s.implicits._
    org.http4s.server.middleware.Logger.httpApp(logHeaders = true, logBody = true)(
      Router(
        "/status" -> HttpRoutes.of[F] { case GET -> Root => NoContent() }
      , "/"       -> middleware {
                      authMiddleware {
                        AuthedRoutes.of {
                          AppErrorHandler {
                            case        GET -> Root / ApiVersion / "repos"                   as user => implicit val u = user; repoEndpoint.getRepos
                            case        GET -> Root / ApiVersion / "repos2"                  as user => implicit val u = user; repoEndpoint.getRepos2
                            case        GET -> Root / ApiVersion / "homes" / LongVar(homeId) as user => implicit val u = user; homeEndpoint.getHome(HomeId(homeId))
                            // case req @ POST -> Root / ApiVersion / "media"                   as user => repoEndpoint.post(req)
                          }
                        }
                      }
                    }
      ).orNotFound
    )
  }
}


object AppErrorHandler {
  private[this] val logger = getLogger

  import cats.effect.Sync
  import cats.syntax.all._
  import org.http4s.dsl.Http4sDsl
  import org.http4s.{AuthedRequest, Response}

  def apply[T, F[_] : Sync](pf: PartialFunction[AuthedRequest[F, T], F[Response[F]]]): PartialFunction[AuthedRequest[F, T], F[Response[F]]] =
    { case req => Sync[F].handleErrorWith(pf(req)) {
        case app.service.ForbiddenError =>
          val HD = Http4sDsl[F]; import HD._
          Sync[F].delay(logger.info(s"${req.context} does not have the required permissions")) *>
            Forbidden()
        }
     }
}
