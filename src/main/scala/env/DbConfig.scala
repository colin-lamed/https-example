package app.env

import cats.effect.{Async, ContextShift, Resource, Sync}
import cats.syntax.all._
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor

case class DbConfig(
    driver  : String
  , url     : String
  , username: String
  , password: String
  )

object DbConfig {

  def transactor[F[_] : Async : ContextShift : Sync](ds: DbConfig): Resource[F, HikariTransactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32)
      te <- ExecutionContexts.cachedThreadPool[F]
      xa <- HikariTransactor.newHikariTransactor[F](
                ds.driver
              , ds.url
              , ds.username
              , ds.password
              , ce
              , te
              )
    } yield xa
}
