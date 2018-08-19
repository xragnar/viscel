package viscel.crawl

import java.time.Instant

import viscel.narration.Narrator
import viscel.scribe.{Book, ImageRef, Link, PageData, Scribe, Vurl}

import scala.concurrent.{ExecutionContext, Future}




class Crawl(narrator: Narrator,
            scribe: Scribe,
            requestUtil: RequestUtil)
           (implicit ec: ExecutionContext) {

  val book: Book = scribe.findOrCreate(narrator)

  def start(): Future[Unit] = {
    val entry = book.beginning
    if (entry.isEmpty || entry.get.contents != narrator.archive) {
      scribe.addRowTo(book, PageData(Vurl.entrypoint, Vurl.entrypoint, date = Instant.now(), contents = narrator.archive))
    }
    val decider = new Decider(
      images = book.emptyImageRefs(),
      links = book.volatileAndEmptyLinks(),
      book = book)

    interpret(decider)
  }

  def interpret(decider: Decider): Future[Unit] = {
    val decision = decider.tryNextImage()
    decision match {
      case ImageD(image) => handleImage(image).flatMap(_ => interpret(decider))
      case LinkD(link) => handleLink(link, decider).flatMap(_ => interpret(decider))
      case Done => Future.successful(())
    }
  }

  private def handleImage(image: ImageRef): Future[Unit] =
    requestUtil.requestBlob(image.ref, Some(image.origin)).map(scribe.addRowTo(book, _))

  private def handleLink(link: Link, decider: Decider) =
    requestUtil.requestPage(link, narrator) map { page =>
      decider.addContents(page.contents)
      scribe.addRowTo(book, page)
    }


}
