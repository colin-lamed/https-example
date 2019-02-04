package app.model

import java.time.Instant
import scala.util.Try


case class Repo(
    name        : String
  , lastModified: Instant
  , lastUpdated : Instant
  , children    : Seq[Artefact]
  )

case class Artefact(
    uri   : String
  , `type`: ArtefactType
  )

sealed trait ArtefactType
object ArtefactType {
  case object Folder extends ArtefactType
  case object Slug   extends ArtefactType
}


case class HomeId(toLong: Long)

object HomeId {
  object StrExtractor {
    def unapply(id: String): Option[HomeId] =
      Try(id.toLong).map(HomeId.apply).toOption
  }
}

case class Home(
    id      : HomeId
  , address : String
  , postcode: String
  )