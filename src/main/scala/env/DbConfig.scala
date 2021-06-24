package app.env

import cats.effect.{Async, Resource}
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor

case class DbConfig(
    driver  : String
  , url     : String
  , username: String
  , password: String
  )

object DbConfig {

  def transactor[F[_] : Async](ds: DbConfig): Resource[F, HikariTransactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32)
      xa <- HikariTransactor.newHikariTransactor[F](
                ds.driver
              , ds.url
              , ds.username
              , ds.password
              , ce
              )
    } yield xa
}
