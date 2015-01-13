package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.SelectUtil._
import viscel.narration.{Metarrator, Narrator, Selection}
import viscel.shared.Story.{Asset, More}
import viscel.shared.{Story, ViscelUrl}

import scala.Predef.augmentString

object Fakku {

	val baseURL = new URL("https://fakku.net/")
	val extractID = ".*/(?:manga|doujinshi)/([^/]+)/read".r
	
	case class FKU(override val id: String, override val name: String, url: String) extends Narrator {
		override def archive: List[Story] = More(url, "") :: Nil

		val findStr = "window.params.thumbs = "
		val extractPos = ".*\\D(\\d+)\\.\\w+".r

		override def wrap(doc: Document, kind: String): List[Story] = storyFromOr(Selection(doc).many("head script").wrap { scripts =>
			val jsSrc = scripts.map(_.html()).mkString("\n")
			val start = jsSrc.indexOf(findStr) + findStr.length
			val end = jsSrc.indexOf("\n", start) - 1
			extract { upickle.read[List[String]](jsSrc.substring(start, end)).map(_.replaceAll("thumbs/(\\d+).thumb", "images/$1")).map(new URL(baseURL, _).toString) }.map(_.map { url =>
				val extractPos(pos) = url
				Asset(url, s"$url#page=$pos")
			})
		})
	}

	def create(_name: String, url: String): FKU = {
		val _id = url match {
			case extractID(eid) => eid
			case _ => throw new IllegalArgumentException(s"could not find id for $url")
		}
		FKU(_id, s"[FK] ${_name}", url)
	}
	


	object Meta extends Metarrator[FKU]("Fakku"){

		def handles(url: ViscelUrl): Boolean = new URL(url).getHost == baseURL.getHost

		override def archive: ViscelUrl = "https://www.fakku.net/"

		def wrap(doc: Document): List[FKU] Or Every[ErrorMessage] = {
			val current = Selection(doc).all("#content > div.content-wrap.doujinshi")
			val currentUrl_? = current.optional("a.button.green").wrapEach(e => Good(e.attr("abs:href")))
			val currentName_? = current.optional("h1[itemprop=name]").wrapEach(e => Good(e.text()))

			val rows_? = Selection(doc).all(".content-row a.content-title").get.map(_.map { a => (a.text, a.attr("abs:href") + "/read") })

			val pairs = append(withGood(currentName_?, currentUrl_?){ _ zip _ }.recover(_ => Nil), rows_?)

			pairs.map(_.map((create _).tupled))
		}
	}

}