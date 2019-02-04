package app.env

import com.typesafe.config.{ConfigFactory, Config ⇒ TSConfig}
import scala.concurrent.duration.{Duration, FiniteDuration}

class Config(val underlying: TSConfig) {

  private def getDuration(key: String): FiniteDuration =
    Duration.fromNanos(underlying.getDuration(key).toNanos)

  val artifactoryUri: String = underlying.getString("artifactory.uri")
  val homeUri       : String = underlying.getString("home.uri")
  val ssoUri        : String = underlying.getString("sso.uri")

  val timeoutDuration: FiniteDuration  = getDuration("timeoutDuration")

  val ds = DbConfig(
      driver   = underlying.getString("db.driver")
    , url      = underlying.getString("db.url")
    , username = underlying.getString("db.username")
    , password = underlying.getString("db.password")
    )
}

object Config {
  def apply() =
    new Config(ConfigFactory.load(getClass.getClassLoader))

  def apply(overrides: String) =
    new Config(
      ConfigFactory.parseString(overrides).
        withFallback(ConfigFactory.load(getClass.getClassLoader))
    )
}
