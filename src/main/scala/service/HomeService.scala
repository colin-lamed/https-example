package app.service

import cats.effect.{Async, Sync}
import cats.implicits._
import io.circe.Decoder
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{AuthScheme, Credentials, Request, Status, Uri}
import org.http4s.headers.Authorization
import org.log4s.getLogger

import app.env.{EnvAsk, ReqEnvAsk}
import app.model.{Home, HomeId}

object HomeService {

  implicit val homeIdDecoder: Decoder[HomeId] =
    Decoder.instance(_.as[String]).emap {
      case HomeId.StrExtractor(homeId) => Right(homeId)
      case str                         => Left(s"$str was an invalid homeURI")
    }

  val homeDecoder: Decoder[Home] =
    ( Decoder.instance(_.get[HomeId]("id"))
    , Decoder.instance(_.get[String]("address"))
    , Decoder.instance(_.get[String]("postcode"))
    ).mapN(Home.apply)
}

class HomeService[F[_] : Async](implicit F: Sync[F], A: EnvAsk[F]) extends Http4sClientDsl[F] {

  private[this] val logger = getLogger

  def getHome(homeId: HomeId)(implicit RA: ReqEnvAsk[F]): F[Option[Home]] =
    handleException("HomeService.getHome"){
      for {
        _       <- F.delay(logger.debug("in getHome"))
        homeUri <- A.reader(_.config.homeUri)
        client  <- A.reader(_.client)
        token   <- RA.reader(_.user.token)
        uri     =  Uri.unsafeFromString(s"$homeUri/homes/${homeId.toLong}")
        request =  Request[F](uri = uri)
                     .putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
        res     <- { implicit val hd = HomeService.homeDecoder
                     client
                       .run(request)
                       .use[Option[Home]] {
                         case Status.Ok(resp)    => jsonOf[F, Option[Home]].decode(resp, strict = false).fold(throw _, identity)
                         case Status.NotFound(_) => F.pure(None)
                         case resp               => onError("HomeApi.getHome")(resp).flatMap(F.raiseError)
                       }
                   }
        _       <- F.delay(logger.debug(s"returning $res"))
      } yield res
    }
}
