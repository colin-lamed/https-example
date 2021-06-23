package app

import cats.effect.Ref
import cats.mtl.Ask
import java.util.UUID
import org.http4s.client.Client

package object env {
  type EnvAsk[F[_]] = Ask[F, Env[F]]
  object EnvAsk {
    def apply[F[_]](implicit A: EnvAsk[F]) = A
  }

  type ReqEnvAsk[F[_]] = Ask[F, ReqEnv]
  object ReqEnvAsk {
    def apply[F[_]](implicit A: ReqEnvAsk[F]) = A
  }
}

package env {

  // -- Global Environment -----------------------

  case class Env[F[_]](
      config: Config
    , client: Client[F]
    , count : Ref[F, Int]
    )


  // -- Request Environment -----------------------

  /** @param requestId may be useful in logging
    *   (We can't use MDC since we use thread pools and context switching - unless implement own ThreadPool to copy context map - inefficient?)
    * @param user the authenticated user
   */
  case class ReqEnv(
      requestId: UUID
    , user     : auth.OAuth.User
    )
}
