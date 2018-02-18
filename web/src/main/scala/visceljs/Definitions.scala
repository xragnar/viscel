package visceljs

import viscel.shared.{Blob, Description}
import visceljs.Actions.{gotoFront, gotoIndex, gotoView, onLeftClick}

import scala.Predef.$conforms
import scala.scalajs.js.URIUtils.encodeURIComponent
import scalatags.JsDom.all.{Frag, SeqFrag, Tag, a, cls, href, stringAttr}


object Definitions {


	def path_main = "#"
	def path_css = "css"
	def path_asset(data: Data) = s"#${encodeURIComponent(data.id)}/${data.pos + 1}"
	def path_search = "s"
	def path_blob(blob: Blob) = s"blob/${blob.sha1}/${blob.mime}"
	def path_front(nar: Description) = s"#${encodeURIComponent(nar.id)}"
	def path_stop = "stop"
	def path_tools = "tools"

	val class_post = cls := "post"
	val class_extern = cls := "extern"
	val class_placeholder = cls := "placeholder"
	val class_dead = cls := "dead"
	val class_preview = cls := "preview"
	val class_chapters = cls := "chapters"


	def link_index(ts: Frag*): Tag = a(onLeftClick(gotoIndex()), href := path_main)(ts)
	def link_stop(ts: Frag*): Tag = a(href := path_stop)(ts)
	def link_tools(ts: Frag*): Tag = a(href := path_tools)(ts)

	def link_asset(data: Data): Tag = link_asset(data, gotoView(data))


	def link_asset(data: Data, onleft: => Unit): Tag =
		if (data.gallery.isEnd) a(class_dead)
		else a.apply(onLeftClick(onleft), href := path_asset(data))
	def link_front(nar: Description, ts: Frag*): Tag = a(onLeftClick(gotoFront(nar)), href := path_front(nar))(ts)


}