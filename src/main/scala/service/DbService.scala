package app.service

import cats.effect.{Async, Sync}
import cats.effect.syntax.monadCancel._
import cats.implicits._
import doobie.{HC, HRS, FCS, FRS}
import doobie.enumerated.JdbcType.Struct
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.log4s.getLogger

import app.env.{EnvAsk/*, ReqEnvAsk*/}

object DbService {
  case class Client(clientId: String, secret: String)
}

class DbService[F[_] : Async : Sync : EnvAsk](xa: Transactor[F]) {

  import DbService._

  private[this] val logger = getLogger

  val x: doobie.util.Read[Client] = implicitly[doobie.util.Read[Client]]

  private def callGetClient(clientId: String): ConnectionIO[Client] =
    HC.prepareCall("{ call oauth_pkg.get_client(?, ?) }") {
      for {
        _      <- FCS.setString(1, clientId)
        _      <- FCS.registerOutParameter(2, Struct.toInt, "CLIENT_TYPE")
        _      <- FCS.delay(logger.debug("executing db call"))
        _      <- FCS.execute
        _      <- FCS.delay(logger.debug("db call executed"))
        client <- FCS.getObject(2).map(_.asInstanceOf[java.sql.ResultSet])
                   .bracket(FCS.embed(_, HRS.get[Client]))(FCS.embed(_, FRS.close))
        _      <- FCS.delay(logger.debug(s"read $client"))
      } yield client
    }

  private def selectGetClient(clientId: String): ConnectionIO[Option[Client]] =
    sql"select client_id, secret from client where client_id = $clientId"
      .query[Client]
      .option

  def getClient(clientId: String): F[Client] =
    for {
      _      <- Sync[F].delay(logger.debug("in getClient"))
      ds     <- EnvAsk[F].reader(_.config.ds)
      _      <- selectGetClient(clientId).transact(xa)
      client <- callGetClient(clientId).transact(xa)
      _      <- if (client.clientId == "1") Sync[F].raiseError(ForbiddenError) else Sync[F].unit
    } yield client
}
