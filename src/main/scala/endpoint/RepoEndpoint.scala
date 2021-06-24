package app.endpoint

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.{Encoder, Json}
import io.circe.syntax._
import org.http4s.{EntityEncoder, Response}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.log4s.getLogger

import app.env.{EnvAsk, ReqEnvAsk}
import app.service.ArtifactoryService
import app.model.{Artefact, ArtefactType, Repo}

object RepoEndpoint {

  val repoEncoder: Encoder[Repo] = {
    implicit val artefactTypeEncoder: Encoder[ArtefactType] =
      Encoder.instance {
        case ArtefactType.Folder => Json.fromString("FOLDER")
        case ArtefactType.Slug   => Json.fromString("SLUG")
      }

    implicit val artefactEncoder: Encoder[Artefact] =
      Encoder.instance { a =>
        Json.obj(
            "uri"  := a.uri
          , "type" := a.`type`
          )
      }

    Encoder.instance { r =>
      Json.obj(
            "repo"         := r.name
          , "lastModified" := r.lastModified
          , "lastUpdated"  := r.lastUpdated
          , "children"     := r.children
        )
    }
  }

}

class RepoEndpoint[F[_] : EnvAsk : Sync](artifactoryService: ArtifactoryService[F]) extends Http4sDsl[F] {

  private[this] val logger = getLogger

  implicit val de = RepoEndpoint.repoEncoder
  implicit val x: EntityEncoder[F, List[Repo]] = jsonEncoderOf[F, List[Repo]]

  def getRepos(implicit RA: ReqEnvAsk[F]): F[Response[F]] =
   for {
     _       <- Sync[F].delay(logger.info("in getRepos"))
     res     <- Ok(artifactoryService.getRepos)
   } yield res

  // pass through example (byte stream)
  def getRepos2(implicit RA: ReqEnvAsk[F]): F[Response[F]] =
    Ok(artifactoryService.getReposAsBytes)


  // def post(req: Request[F]) =
  //   req.as[Person].flatMap { person =>
  //     Ok(s"Successfully decoded person: ${person.name}")
  //   }

}
