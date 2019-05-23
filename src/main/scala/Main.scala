package app

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.effect.concurrent.Ref
import doobie.util.transactor.Transactor
import fs2.Stream
import org.http4s.{AuthedService, HttpApp, HttpRoutes}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.{AuthMiddleware, HttpMiddleware, Router}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{AutoSlash, GZip, RequestLogger, ResponseLogger}
import org.log4s.getLogger
import scala.concurrent.ExecutionContext.Implicits.global

import app.auth.OAuth
import app.endpoint.{RepoEndpoint, HomeEndpoint}
import app.env.{ApplicativeAskImpl, Config, Env, DbConfig, ReqEnv}
import app.model.HomeId
import app.service.{ApiVersion, ArtifactoryService, DbService, HomeService, SsoService}

object Main extends IOApp {
  import cats.implicits._

  override def run(args: List[String]): IO[ExitCode] =
    HttpServer.stream[IO].compile.drain.as(ExitCode.Success)
}


object HttpServer {

  private[this] val logger = getLogger

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer](implicit F : Sync[F]): Stream[F, ExitCode] =
    for {
      client   ← BlazeClientBuilder[F](global).stream
      count    ← Stream.eval(Ref.of(0))
      port     ← Stream.eval(F.delay(sys.props.getOrElse("http.port", "8080").toInt))
      _        ← Stream.eval(F.delay(logger.info(s"Starting server on $port")))
      config   = Config()
      env      = Env( config = config
                    , client = client
                    , count = count
                    )
      exitCode ← Stream.resource(DbConfig.transactor(config.ds)).flatMap { xa =>
                   BlazeServerBuilder[F]
                     .bindHttp(port)
                     .withHttpApp(httpApp(env, xa))
                     .serve
                 }
    } yield exitCode

  def httpApp[F[_]: ConcurrentEffect: ContextShift: Timer](env: Env[F], xa: Transactor[F]): HttpApp[F] = {

    implicit val eia = new ApplicativeAskImpl(env)

    val artifactoryService = new ArtifactoryService[F]
    val dbService          = new DbService[F](xa)
    val homeService        = new HomeService[F]
    val ssoService         = new SsoService[F]

    val middleware: HttpMiddleware[F] =
                { routes: HttpRoutes[F] ⇒ GZip(routes)      }
       .compose { routes: HttpRoutes[F] ⇒ AutoSlash(routes) }

    val authMiddleware: AuthMiddleware[F, OAuth.User] =
      OAuth[F](ssoService)

    val repoEndpoint = new RepoEndpoint(artifactoryService)
    val homeEndpoint = new HomeEndpoint(homeService, dbService)

    implicit def reqEnvImpl(implicit user: OAuth.User) = new ApplicativeAskImpl(ReqEnv(
        requestId = java.util.UUID.randomUUID
      , user      = user
      ))

    val HD = Http4sDsl[F]; import HD._


    ResponseLogger(logHeaders = true, logBody = true)(
      RequestLogger(logHeaders = true, logBody = true)(
        Router(
            "/status" → HttpRoutes.of[F] { case GET -> Root ⇒ NoContent() }
          , "/"       → middleware {
                          authMiddleware {
                            AuthedService {
                              case        GET -> Root / ApiVersion / "repos"                   as user ⇒ implicit val u = user; repoEndpoint.getRepos
                              case        GET -> Root / ApiVersion / "repos2"                  as user ⇒ implicit val u = user; repoEndpoint.getRepos2
                              case        GET -> Root / ApiVersion / "homes" / LongVar(homeId) as user ⇒ implicit val u = user; homeEndpoint.getHome(HomeId(homeId))
                              // case req @ POST -> Root / ApiVersion / "media"    as user ⇒ repoEndpoint.post(req)
                            }
                          }
                      }
          ).orNotFound
      )
    )
  }
}
