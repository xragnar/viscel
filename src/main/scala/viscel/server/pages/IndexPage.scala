package viscel.server.pages

import viscel.database.Ntx
import viscel.server.HtmlPage
import viscel.store.{Vault, User}

import scala.Predef.conforms
import scalatags.Text.all.SeqFrag
import scalatags.Text.attrs.id
import scalatags.Text.implicits.{stringAttr, stringFrag}
import scalatags.Text.tags.{body, div}
import scalatags.Text.{Tag, TypedTag}

class IndexPage(user: User)(implicit ntx: Ntx) extends HtmlPage {
	override def Title = "Index"
	override def bodyId = "index"

	override def sidePart = make_fieldset("Search", Seq(form_search("")))(class_info) :: Nil

	override def navigation = link_stop("stop") :: Nil

	override def mainPart: List[TypedTag[String]] = {
		val (hasNewPages, isCurrent) = (for {
			(colid, pos) <- user.bookmarks
			collection <- Vault.find.collection(colid)
			bm <- collection(pos)
		} yield {(collection, collection.name, bm.distanceToLast)}).partition { _._3 > 0 }
		val unreadTags = hasNewPages.toList.sortBy { -_._3 }.map { case (collection, name, unread) => link_node(collection, s"$name ($unread)") }
		val currentTags = isCurrent.toList.sortBy { _._2 }.map { case (collection, name, unread) => link_node(collection, s"$name") }
		//val availableCores = Core.availableCores.map { core => link_core(core) }.toSeq
		//		val allCollections = Neo.nodes(viscel.store.label.Collection).map(CollectionNode(_)).sortBy(_.name).map { collection =>
		//			link_node(collection, s"${ collection.name }")
		//		}

		make_fieldset("New Pages", unreadTags)(class_group) ::
			make_fieldset("Bookmarks", currentTags)(class_group) ::
			//make_fieldset("All Collections", allCollections)(class_group) ::
			//make_fieldset("Available Cores", availableCores)(class_group) ::
			Nil
	}

	override def content: Tag = body(id := bodyId)(
		div(class_main)(mainPart),
		div(class_side)(sidePart),
		div(class_navigation)(navigation))
}