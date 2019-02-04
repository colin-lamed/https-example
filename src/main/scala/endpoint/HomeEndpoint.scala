package app.endpoint

import cats.effect.{Effect, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.{Encoder, Json}
import io.circe.syntax._
import org.http4s.EntityEncoder
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.log4s.getLogger

import app.env.{EnvAsk, ReqEnvAsk}
import app.service.{DbService, HomeService}
import app.model.{Home, HomeId}

object HomeEndpoint {
  val homeEncoder: Encoder[Home] =
    Encoder.instance { h ⇒
      Json.obj(
          "id"       := h.id.toLong
        , "address"  := h.address
        , "postcode" := h.postcode
        )
    }
}

class HomeEndpoint[F[_] : Effect : EnvAsk : Sync](homeService: HomeService[F], dbService: DbService[F]) extends Http4sDsl[F] {

  private[this] val logger = getLogger

  implicit val he = HomeEndpoint.homeEncoder
  implicit val x: EntityEncoder[F, Home] = jsonEncoderOf[F, Home]

  def getHome(homeId: HomeId)(implicit RA: ReqEnvAsk[F]): F[org.http4s.Response[F]] =
    for {
      _       ← Sync[F].delay(logger.info("in getHome"))
      optHome ← homeService.getHome(homeId)
      //client  ← dbService.getClient("clientId") // TODO set up db in test setup
      //_       ← Sync[F].delay(logger.info(s"client = $client"))
      res     ← optHome.fold(NotFound())(Ok(_))
    } yield res
}
