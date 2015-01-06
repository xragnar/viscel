package visceljs.render

import viscel.shared.Story.Narration
import visceljs.Definitions.{link_front, link_stop}
import visceljs.{Body, Make}

import scala.Predef.$conforms
import scala.collection.immutable.Map
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.SeqFrag


object Index {
	def gen(bookmarks: Map[String, Int], narrations: Map[String, Narration]): Body = {

		val result: List[(Narration, Int, Int)] =
			bookmarks.map { case (id, pos) =>
				narrations.get(id).map { nr =>
					(nr, pos, nr.size - pos)
				}
			}.toList.flatten

		val (hasNewPages, isCurrent) = result.partition(_._3 > 0)

		val unreadTags = hasNewPages.sortBy(-_._3).map {
			case (nr, pos, unread) => link_front(nr, s"${ nr.name } ($unread)")
		}
		val currentTags = isCurrent.sortBy(_._1.name).map {
			case (nr, pos, unread) => link_front(nr, s"${ nr.name }")
		}


		Body(id = "index", title = "Viscel",
			frag = List(
				Make.group("New Pages", unreadTags),
				Make.group("Bookmarks", currentTags),
				Make.navigation(Make.fullscreenToggle("TFS"),link_stop("stop")),
				Make.searchArea(narrations.values.toList)))
	}
}
