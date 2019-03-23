package viscel.narration

import org.jsoup.helper.StringUtil
import org.jsoup.nodes.Element
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic._
import viscel.narration.interpretation.NarrationInterpretation.{Append, WrapPart, Wrapper}
import viscel.selection.ReportTools._
import viscel.selection.{FailedElement, QueryNotUnique, Report, Selection, UnhandledTag}
import viscel.store.{Chapter, ImageRef, Link, Vurl, WebContent}

import scala.util.matching.Regex

object Queries {

  /** tries to extract an absolute uri from an element, extraction depends on type of tag */
  def extractURL(element: Element): Vurl Or One[Report] = element.tagName() match {
    case "a" => extract {Vurl.fromString(element.attr("abs:href"))}
    case "link" => extract {Vurl.fromString(element.attr("abs:href"))}
    case "option" => extract {Vurl.fromString(element.attr("abs:value"))}
    case _ => Bad(One(FailedElement(s"extract uri", UnhandledTag, element)))
  }

  def selectMore(elements: List[Element]): List[Link] Or Every[Report] =
    if (elements.isEmpty) Good(Nil)
    else elements.validatedBy(extractMore).flatMap {
      case pointers if pointers.toSet.size == 1 => Good(pointers.headOption.toList)
      case pointers => Bad(One(FailedElement("next not unique", QueryNotUnique, elements: _*)))
    }

  def extractMore(element: Element): Link Or Every[Report] =
    extractURL(element).map(uri => Link(uri))

  def intoArticle(img: Element): ImageRef Or Every[Report] = {
    def getAttr(k: String): Option[(String, String)] = {
      val res = img.attr(k)
      if (res.isEmpty) None else Some(k -> res)
    }

    img.tagName() match {
      case "img" =>
        extract(ImageRef(
          ref = Vurl.fromString(img.attr("abs:src")),
          origin = Vurl.fromString(img.ownerDocument().location()),
          data = List("alt", "title", "width", "height").flatMap(getAttr).toMap))
      case "embed" =>
        extract(ImageRef(
          ref = Vurl.fromString(img.attr("abs:src")),
          origin = Vurl.fromString(img.ownerDocument().location()),
          data = List("width", "height", "type").flatMap(getAttr).toMap))
      case "object" =>
        extract(ImageRef(
          ref = Vurl.fromString(img.attr("abs:data")),
          origin = Vurl.fromString(img.ownerDocument().location()),
          data = List("width", "height", "type").flatMap(getAttr).toMap))
      case _ =>
        val extractBG = """.*background\-image\:url\(([^)]+)\).*""".r("url")
        img.attr("style") match {
          case extractBG(url) =>
            extract(ImageRef(
              ref = Vurl.fromString(StringUtil.resolve(img.ownerDocument().location(), url)),
              origin = Vurl.fromString(img.ownerDocument().location())))
          case _ => Bad(One(FailedElement(s"into article", UnhandledTag, img)))
        }
    }

  }

  def extractChapter(elem: Element): Chapter Or Every[Report] = extract {
    def firstNotEmpty(choices: String*) = choices.find(!_.isBlank).getOrElse("")

    Chapter(firstNotEmpty(elem.text(), elem.attr("title"), elem.attr("alt")))
  }

  def queryImage(query: String): WrapPart[List[ImageRef]] = Selection.unique(query).wrapEach(intoArticle)
  def queryImages(query: String): WrapPart[List[ImageRef]] = Selection.many(query).wrapEach(intoArticle)
  /** extracts article at query result
    * optionally extracts direct parent of query result as link */
  def queryImageInAnchor(query: String): Wrapper = Selection.unique(query).wrapFlat[WebContent] { image =>
    intoArticle(image).map[List[WebContent]] { (art: ImageRef) =>
      val wc: List[WebContent] = extractMore(image.parent()).toOption.toList
      art ::  wc }
  }
  def queryNext(query: String): WrapPart[List[Link]] = Selection.all(query).wrap(selectMore)
  def queryImageNext(imageQuery: String, nextQuery: String): Wrapper = {
    Append(queryImage(imageQuery), queryNext(nextQuery))
  }
  def queryMixedArchive(query: String): Wrapper = {
    def intoMixedArchive(elem: Element): WebContent Or Every[Report] = {
      if (elem.tagName() === "a") extractMore(elem)
      else extractChapter(elem)
    }

    Selection.many(query).wrapEach {intoMixedArchive}
  }

  def queryChapterArchive(query: String): Wrapper = {
    /** takes an element, extracts its uri and text and generates a description pointing to that chapter */
    def elementIntoChapterPointer(chapter: Element): Contents =
      combine(extractChapter(chapter), extractMore(chapter))

    Selection.many(query).wrapFlat(elementIntoChapterPointer)
  }


  def chapterReverse(stories: List[WebContent]): List[WebContent] = {
    def groupedOn[T](l: List[T])(p: T => Boolean): List[List[T]] = l.foldLeft(List[List[T]]()) {
      case (acc, t) if p(t) => List(t) :: acc
      case (Nil, t)         => List(t) :: Nil
      case (a :: as, t)     => (t :: a) :: as
    }.map(_.reverse).reverse

    groupedOn(stories) { case Chapter(_) => true; case _ => false }.reverse.flatMap {
      case h :: t => h :: t.reverse
      case Nil    => Nil
    }

  }

  implicit class RegexContext(val sc: StringContext) {
    object rex {
      def unapplySeq(m: String): Option[Seq[String]] = {
        val regex = new Regex(sc.parts.mkString(""))
        regex.findFirstMatchIn(m).map { gs =>
          gs.subgroups
        }
      }
    }
  }


}
