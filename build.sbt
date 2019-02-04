val Http4sVersion   = "0.19.0"
val CirceVersion    = "0.10.0"
val CatsMtlVersion  = "0.4.0"
val ConfigVersion   = "1.3.2"
val LogbackVersion  = "1.2.3"
val Log4sVersion    = "1.6.1"
val DoobieVersion   = "0.6.0"
val PostgresqlVersion = "42.2.5"
val Specs2Version   = "4.1.0"
val H2Version       = "1.4.197"

lazy val root = (project in file("."))
  .settings(
    organization := "com.example"
    , name         := "scala-http4s"
    , version      := "0.0.1-SNAPSHOT"
    , scalaVersion := "2.12.7"
    , libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-blaze-server" % Http4sVersion
      , "org.http4s"      %% "http4s-circe"        % Http4sVersion
      , "org.http4s"      %% "http4s-dsl"          % Http4sVersion
      , "org.http4s"      %% "http4s-blaze-client" % Http4sVersion
      , "io.circe"        %% "circe-generic"       % CirceVersion
      , "org.typelevel"   %% "cats-mtl-core"       % CatsMtlVersion
      , "com.typesafe"    %  "config"              % ConfigVersion
      , "org.log4s"       %% "log4s"               % Log4sVersion
      , "ch.qos.logback"  %  "logback-classic"     % LogbackVersion
      , "org.tpolecat"    %% "doobie-core"         % DoobieVersion
      , "org.tpolecat"    %% "doobie-hikari"       % DoobieVersion
      , "org.postgresql"  %  "postgresql"          % PostgresqlVersion
      , "org.specs2"      %% "specs2-core"         % Specs2Version % Test
      , "com.h2database"  %  "h2"                  % H2Version     % Test
      )
    , addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6")
    , addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
    )

// scalacOptions set by sbt-tpolecat plugin - disable broken option
scalacOptions.in(root) ~= { options: Seq[String] => options.filterNot(Set("-Ywarn-unused:params")) }
