package viscel.server

import com.typesafe.scalalogging.slf4j.Logging
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scalatags._
import spray.http.{ HttpResponse, HttpEntity, MediaTypes, ContentType, HttpCharsets }
import viscel.store.CollectionNode
import viscel.store.ElementNode
import viscel.store.Neo
import viscel.store.UserNode
import viscel.store.ViscelNode
import viscel.store.{ Util => StoreUtil }
import viscel.time

trait MaskLocation {
	page: HtmlPage =>

	def maskLocation: String

	override def header: HtmlTag = page.header(maskLocation.map { loc => script(s"window.history.replaceState('param one?','param two?','${loc}')") }.toSeq)
}

trait HtmlPageUtils {

	def path_main = "/index"
	def path_css = "/css"
	def path_front(id: String) = s"/f/$id"
	def path_view(id: String, pos: Int) = s"/v/$id/$pos"
	def path_search = "/s";
	def path_blob(id: String) = s"/b/$id"
	def path_nid(id: Long) = s"/id/$id"
	def path_stop = "/stop"

	def link_main(ts: STag*) = a.href(path_main)(ts)
	def link_stop(ts: STag*) = a.href(path_stop)(ts)
	def link_front(id: String, ts: STag*) = a.href(path_front(id))(ts)
	def link_view(id: String, pos: Int, ts: STag*) = a.href(path_view(id, pos))(ts)
	def link_node(vn: ViscelNode, ts: STag*): STag = a.href(path_nid(vn.nid))(ts)
	def link_node(vn: Option[ViscelNode], ts: STag*): STag = vn.map { link_node(_, ts: _*) }.getOrElse(ts)
	// def link_node(en: Option[ElementNode], ts: STag*): STag = en.map{n => link_view(n.collection.id, n.position, ts)}.getOrElse(ts)

	def form_post(action: String, ts: STag*) = form.attr("method" -> "post", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)
	def form_get(action: String, ts: STag*) = form.attr("method" -> "get", "enctype" -> MediaTypes.`application/x-www-form-urlencoded`.toString).action(action)(ts)

	def searchForm(init: String) = form_get(path_search, input.ctype("textfield").name("q").value(init))

	def enodeToImg(en: ElementNode) = img.src(path_blob(en[String]("blob"))).cls("element")
		.attr { Seq("width", "height").flatMap { k => en.get[Int](k).map { k -> _ } } ++ Seq("alt", "title").flatMap { k => en.get[String](k).map { k -> _ } }: _* }

}

trait JavascriptNavigation {
	page: HtmlPage =>

	override def header: HtmlTag = page.header(script(scala.xml.Unparsed(keyNavigation(up = navUp, down = navDown, prev = navPrev, next = navNext))))

	def navUp: Option[String] = None
	def navNext: Option[String] = None
	def navPrev: Option[String] = None
	def navDown: Option[String] = None

	def keypress(location: String, keyCodes: Int*) = s"""
			|if (${keyCodes.map { c => s"ev.keyCode == $c" }.mkString(" || ")}) {
			|	ev.preventDefault();
			|	document.location.pathname = "${location}";
			|	return false;
			|}
			""".stripMargin

	def keyNavigation(prev: Option[String] = None, next: Option[String] = None, up: Option[String] = None, down: Option[String] = None) = s"""
			|document.onkeydown = function(ev) {
			|	if (!ev.ctrlKey && !ev.altKey) {
			|${prev.map { loc => keypress(loc, 37, 65, 188) }.getOrElse("")}
			|${next.map { loc => keypress(loc, 39, 68, 190) }.getOrElse("")}
			|${up.map { loc => keypress(loc, 13, 87, 77) }.getOrElse("")}
			|${down.map { loc => keypress(loc, 40, 83, 66, 78) }.getOrElse("")}
			| }
			|}
			""".stripMargin

}