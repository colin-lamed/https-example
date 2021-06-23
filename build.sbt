val Http4sVersion   = "1.0.0-M23"
val CirceVersion    = "0.14.1"
val CatsMtlVersion  = "1.2.0"
val ConfigVersion   = "1.4.1"
val LogbackVersion  = "1.2.3"
val Log4sVersion    = "1.8.2"
val DoobieVersion   = "1.0.0-M2"
val PostgresqlVersion = "42.2.5"
val Specs2Version   = "4.12.1"
val H2Version       = "1.4.197"

lazy val root = (project in file("."))
  .settings(
      organization := "com.example"
    , name         := "scala-http4s"
    , version      := "0.0.1-SNAPSHOT"
    , scalaVersion := "2.13.2"
    , libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-blaze-server" % Http4sVersion
      , "org.http4s"      %% "http4s-circe"        % Http4sVersion
      , "org.http4s"      %% "http4s-dsl"          % Http4sVersion
      , "org.http4s"      %% "http4s-blaze-client" % Http4sVersion
      , "io.circe"        %% "circe-generic"       % CirceVersion
      , "org.typelevel"   %% "cats-mtl-laws"       % CatsMtlVersion
      , "com.typesafe"    %  "config"              % ConfigVersion
      , "org.log4s"       %% "log4s"               % Log4sVersion
      , "ch.qos.logback"  %  "logback-classic"     % LogbackVersion
      , "org.tpolecat"    %% "doobie-core"         % DoobieVersion
      , "org.tpolecat"    %% "doobie-hikari"       % DoobieVersion
      , "org.postgresql"  %  "postgresql"          % PostgresqlVersion
      , "org.specs2"      %% "specs2-core"         % Specs2Version % Test
      , "com.h2database"  %  "h2"                  % H2Version     % Test
      )
    , addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.0" cross CrossVersion.full)
    , addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    )

// scalacOptions set by sbt-tpolecat plugin - disable broken options
scalacOptions --= Seq(
    "-Wunused:params"
  , "-Wunused:explicits"
  )
