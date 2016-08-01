package viscel.narration.narrators

import java.net.URL

import org.jsoup.nodes.Document
import org.scalactic.{Every, Or}
import upickle.default
import viscel.narration.Queries._
import viscel.narration.{Metarrator, Templates}
import viscel.selection.{Report, Selection}

object Batoto {

	case class Btt(cid: String, cname: String, start: String) extends Templates.AP(start,
		queryChapterArchive("#content table.chapters_list tr.lang_English.chapter_row a[href~=http://bato.to/read/]")(_).map(reverse),
		queryImageInAnchor("#comic_page")
	) {
		override def id: String = s"Batoto_$cid"
		override def name: String = s"[BT] $cname"
	}

	object Meta extends Metarrator[Btt]("Batoto") {
		override def reader: default.Reader[Btt] = implicitly[default.Reader[Btt]]
		override def writer: default.Writer[Btt] = implicitly[default.Writer[Btt]]

		override def unapply(description: String): Option[URL] = description match {
			case rex"^http://bato.to/comic/_/comics/" => Some(stringToURL(description))
			case _ => None
		}


		override def wrap(document: Document): Or[List[Btt], Every[Report]] = {
			val rex"^http://bato.to/comic/_/comics/($id[^/]+)" = document.baseUri()
			Selection(document).unique("#content h1.ipsType_pagetitle").getOne.map(e => Btt(java.net.URLDecoder.decode(id, "UTF-8"), e.text(), document.baseUri()) :: Nil)
		}
	}

}