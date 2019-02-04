package app.service

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import io.circe.Decoder
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Header, Request, Uri}
import org.log4s.getLogger

import app.env.EnvAsk

object SsoService {
  case class User(name: String)

  val userDecoder: Decoder[User] =
    Decoder.instance(_.get[String]("username")).map(User.apply)
}

class SsoService[F[_]: Monad](implicit A: EnvAsk[F], F: Sync[F]) extends Http4sClientDsl[F] {
  import SsoService._

  private[this] val logger = getLogger

  def getUser(accessToken: String): F[User] =
    handleException("Sso.getUser") {
      for {
        _       ← F.delay(logger.debug("in getUser"))
        ssoUri  ← A.reader(_.config.ssoUri)
        client  ← A.reader(_.client)
        uri     = Uri.unsafeFromString(ssoUri)
                    .withPath(s"/sso/v/1/user")
        request = Request[F](uri = uri)
                    .putHeaders(Header("Authorization", s"Bearer $accessToken"))
        res     ← { implicit val ud = userDecoder
                    client
                      .expectOr[User](request)(onError("Sso.getUser"))(jsonOf[F, User])
                  }
        _       ← F.delay(logger.debug(s"returning $res"))
      } yield res
    }
}