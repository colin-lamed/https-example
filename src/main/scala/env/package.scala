package app

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.mtl.{ApplicativeAsk, DefaultApplicativeAsk}
import java.util.UUID
import org.http4s.client.Client

package object env {
  type EnvAsk[F[_]] = ApplicativeAsk[F, Env[F]]
  object EnvAsk {
    def apply[F[_]](implicit A: EnvAsk[F]) = A
  }

  type ReqEnvAsk[F[_]] = ApplicativeAsk[F, ReqEnv]
  object ReqEnvAsk {
    def apply[F[_]](implicit A: ReqEnvAsk[F]) = A
  }
}

package env {

  class ApplicativeAskImpl[F[_] : Applicative, E](env: E) extends ApplicativeAsk[F, E] with DefaultApplicativeAsk[F, E] {
    override val applicative = implicitly[Applicative[F]]
    override def ask = applicative.pure(env)
  }

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
