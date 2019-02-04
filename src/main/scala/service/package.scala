package app

import cats.effect.Sync
import cats.syntax.all._
import org.http4s.{Response, Status}
import org.log4s.getLogger
import scala.util.control.NoStackTrace

package object service {

  private[this] val logger = getLogger

  val ApiVersion = "v1"

  def onError[F[_] : Sync](api: String)(resp: Response[F]): F[Throwable] =
    for {
      body ← resp.body.compile.toVector
    } yield ServiceStatusError(api, new String(body.toArray), resp.status)

  /** log and convert Throwables
    * Without this, an internal error, e.g. Bad Json, would propagate and be reported as a Client error.
    */
  def handleException[F[_], A](api: String)(f: F[A])(implicit F: Sync[F]): F[A] =
    f.handleErrorWith { t =>
      F.delay(logger.error(t)(s"Failed to call $api")) *>
        F.raiseError(ServiceError(api, t.getMessage))
    }
}

package service {

  final case class ServiceStatusError(api: String, msg: String, status: Status) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"Call to $api failed: status: $status, msg: $msg"
  }

  final case class ServiceError(api: String, msg: String) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"Call to $api failed: msg: $msg"
  }

}