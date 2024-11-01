package app

import org.apache.pekko.http.scaladsl.model.Uri

package object model {

  final case class UrlRequest(urls: List[Uri])

  sealed trait TitleAnswer

  final case class Ok(title: String) extends TitleAnswer
  final case class Err(status: Option[Int], message: String) extends TitleAnswer

  final case class Title(uri: Uri, answer: TitleAnswer)

}
