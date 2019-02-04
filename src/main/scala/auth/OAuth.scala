package app.auth

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.instances.all._
import cats.syntax.all._
import org.http4s.{AuthedService, Challenge, Request}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.headers.{Authorization, `WWW-Authenticate`}
import org.log4s.getLogger

import app.service.SsoService

object OAuth {
  case class User(name: String, token: String)

  private[this] val logger = getLogger

  def apply[F[_]](ssoService: SsoService[F])(implicit F : Sync[F]): AuthMiddleware[F, User] = {
    val HD = Http4sDsl[F]; import HD._
    val authUser: Kleisli[F, Request[F], Either[String, User]] = Kleisli { req ⇒
      val eUser = for {
                    auth  ← Either.fromOption(req.headers.get(`Authorization`), "No auth header")
                    token ← if (auth.value.startsWith("Bearer ")) Right(auth.value.stripPrefix("Bearer ")) else Left("Invalid auth header")
                  } yield for {
                      u ← ssoService.getUser(token)
                      _ ← F.delay(logger.debug(s"user = ${u}"))
                    } yield User(u.name, token)
      eUser.sequence
    }
    val onFailure: AuthedService[String, F] = Kleisli { req ⇒
      OptionT.liftF {
        F.delay(logger.info(s"Failed: ${req.authInfo}")) *>
          Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "Protected Realm")))
      }
    }
    AuthMiddleware(authUser, onFailure)
  }
}