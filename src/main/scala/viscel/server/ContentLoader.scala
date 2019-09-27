package viscel.server

import viscel.shared.{Blob, ChapterPos, Contents, Description, Gallery, Log, SharedImage, Vid}
import viscel.store._
import viscel.store.v4.{DataRow, RowStoreV4, Vurl}
import cats.implicits.catsSyntaxOptionId
import cats.implicits.catsSyntaxEitherId

import scala.collection.mutable

class ContentLoader(narratorCache: NarratorCache,
                    rowStore: RowStoreV4,
                    descriptionCache: DescriptionCache) {


  def contents(id: Vid): Option[Contents] = {
    // load the book in an order suitable for viewing
    val pages = ContentLoader.linearizedPages(rowStore.loadBook(id))
    if (pages.isEmpty) None
    else Some(ContentLoader.pagesToContents(pages))
  }

  def descriptions(): Map[Vid, Description] = {
    Log.Server.debug(s"requesting descriptions")
    val stored   : Map[Vid, Description] = rowStore.allVids().map { id => id -> description(id) }.toMap
    val narrators: Map[Vid, Description] = narratorCache.all.map { n =>
      stored.get(n.id) match {
        case None       => n.id -> Description(n.name, 0, linked = true,
                                               timestamp = System.currentTimeMillis())
        case Some(desc) => n.id -> desc.copy(linked = true)
      }
    }.toMap
    val res = stored ++ narrators
    Log.Server.debug(s"found ${res.size} descriptions")
    res
  }

  private def description(id: Vid): Description = {
    Log.Server.trace(s"requesting description for $id")
    descriptionCache.getOrElse(id) {
      Log.Server.trace(s"computing description for $id")
      val book = rowStore.loadBook(id)
      Description(book.name, ContentLoader.size(book), linked = false, timestamp = System.currentTimeMillis())
    }
  }


}

object ContentLoader {

  def size(book: Book): Int = ContentLoader.linearizedPages(book).count(_.isLeft)

  case class OriginData(link: DataRow.Link) {

  }

  type LinearResult = List[Either[SharedImage, DataRow.Chapter]]
  def linearizedPages(book: Book): LinearResult = {

    //Log.Scribe.info(s"pages for ${book.id}")

    val seenOrigins = mutable.HashMap[Vurl, Vurl]()


    def unseen(origin: Vurl, contents: List[DataRow.Content]): List[DataRow.Content] = {
      contents.filter {
        case link @ DataRow.Link(loc, data) =>
          if (seenOrigins.contains(loc)) false
          else {
            seenOrigins += (loc -> origin)
            true
          }
        case _                              => true
      }
    }

    @scala.annotation.tailrec
    def flatten(lastLink: Option[DataRow.Link],
                remaining: List[DataRow.Content],
                acc: LinearResult): LinearResult = {
      remaining match {
        case Nil    => acc
        case h :: t => h match {
          case l @ DataRow.Link(loc, _) =>
            book.pages.get(loc) match {
              case None      => flatten(lastLink, t, acc)
              case Some(alp) => flatten(l.some,
                                        unseen(alp.ref, alp.contents) reverse_::: t,
                                        acc)
            }
          case DataRow.Blob(sha1, mime) =>
            val (dataMap, origin) = lastLink.map { ll =>
              ll.data.sliding(2, 2).filter(_.size == 2).map {
                case List(a, b) => a -> b
              }.toMap -> seenOrigins(ll.ref).uriString()
            }.getOrElse(Map.empty -> "")
            flatten(lastLink, t,
                    SharedImage(origin,
                                Blob(sha1, mime),
                                dataMap).asLeft :: acc)
          case ch: DataRow.Chapter      => flatten(lastLink, t, ch.asRight :: acc)
        }
      }
    }

    book.beginning match {
      case None              =>
        Log.Scribe.warn(s"Book ${book.id} was empty")
        Nil
      case Some(initialPage) =>
        flatten(None, unseen(initialPage.ref, initialPage.contents.reverse), Nil)
    }

  }

  def pagesToContents(pages: LinearResult): Contents = {
    @scala.annotation.tailrec
    def recurse(content: LinearResult,
                images: List[SharedImage],
                chapters: List[ChapterPos],
                counter: Int)
    : (List[SharedImage], List[ChapterPos]) = {
      content match {
        case Nil    => (images, chapters)
        case h :: t =>
          h match {
            case Left(article)        =>
              recurse(t, article :: images, if (chapters.isEmpty) List(ChapterPos("", 0)) else chapters, counter + 1)
            case Right(chap) =>
              recurse(t, images, ChapterPos(chap.name, counter) :: chapters, counter)
          }
      }
    }

    val (articles, chapters) = recurse(pages, Nil, Nil, 0)
    Contents(Gallery.fromSeq(articles.reverse), chapters)
  }

}
