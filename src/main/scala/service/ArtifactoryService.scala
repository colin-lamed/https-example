package app.service

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import io.circe.Decoder
import java.time.Instant
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Header, Request, Uri}
import org.log4s.getLogger

import app.env.{EnvAsk, ReqEnvAsk}
import app.model.{Artefact, ArtefactType, Repo}

object ArtifactoryService {

  val repoDecoder: Decoder[Repo] = {
    implicit val artefactDecoder: Decoder[Artefact] =
      ( Decoder.instance(_.get[String]("uri"))
      , Decoder.instance(_.get[Boolean]("isFolder")).map {
         case true  => ArtefactType.Folder
         case false => ArtefactType.Slug
      }
      ).mapN(Artefact.apply)

    ( Decoder.instance(_.get[String]("repo"))
    , Decoder.instance(_.get[Instant]("lastModified"))
    , Decoder.instance(_.get[Instant]("lastUpdated"))
    , Decoder.instance(_.get[Seq[Artefact]]("children"))
    ).mapN(Repo.apply)
  }
}

class ArtifactoryService[F[_]: Monad](implicit A: EnvAsk[F], F: Sync[F]) extends Http4sClientDsl[F] {

  private[this] val logger = getLogger

  def getRepos(implicit RA: ReqEnvAsk[F]): F[List[Repo]] =
    handleException("Artifactory.getRepos") {
      for {
        _              ← F.delay(logger.debug("in getRepos"))
        artifactoryUri ← A.reader(_.config.artifactoryUri)
        client         ← A.reader(_.client)
        countRef       ← A.reader(_.count)
        token          ← RA.reader(_.user.token)
        count          ← countRef.modify(x => (x + 1, x))
        _              ← F.delay(logger.info(s"count = $count"))
        uri            = Uri.unsafeFromString(artifactoryUri)
                          .withPath(s"/artifactory/v/1/repos")
        request        = Request[F](uri = uri)
                          .putHeaders(Header("Authorization", s"Bearer $token"))
        res            ← { implicit val dd = ArtifactoryService.repoDecoder
                          client
                            .expectOr[List[Repo]](request)(onError("Artifactory.getRepos"))(jsonOf[F, List[Repo]])
                         }
        _              ← F.delay(logger.debug(s"returning $res"))
      } yield res
    }

  def getReposAsBytes(implicit RA: ReqEnvAsk[F]): F[Stream[F, Byte]] =
    handleException("Artifactory.getReposAsBytes") {
      for {
        _       ← F.delay(logger.debug("in getReposAsBytes"))
        artifactoryUri  ← A.reader(_.config.artifactoryUri)
        client  ← A.reader(_.client)
        token   ← RA.reader(_.user.token)
        uri     = Uri.unsafeFromString(artifactoryUri)
                    .withPath(s"/artifactory/v/1/repos")
        request = Request[F](uri = uri)
                    .putHeaders(Header("Authorization", s"Bearer $token"))
      } yield client
                .stream(request).flatMap(_.body)
    }
}