package viscel.server

import org.neo4j.graphdb.Node
import spray.http._
import upickle.Writer
import viscel.database.{Traversal, Ntx}
import viscel.database.Util.listCollections
import viscel.shared.{AbsUri, Story}
import viscel.shared.Story.{Narration, Asset}
import viscel.store.coin.Collection
import viscel.store.{Coin, User}

import scalatags.Text.{TypedTag, RawFrag}
import scalatags.Text.attrs.{href, rel, `type`, title, src, id, cls}
import scalatags.Text.tags.{script, link, head, html, body, div}
import scalatags.Text.implicits.{stringAttr, stringFrag}
import argonaut.{CodecJson, Json}
import scala.Predef.any2ArrowAssoc
import scala.collection.immutable.Map



object ServerPages {
	val path_css: String = "/css"
	val path_js: String = "/js"

	val fullHtml: TypedTag[String] =
		html(
			head(
				link(href := path_css, rel := "stylesheet", `type` := MediaTypes.`text/css`.toString()),
				title := "Viscel"),
			body(id := "index")(div("this page is generated via javascript")),
			script(src := path_js),
			script(RawFrag(s"Viscel().main()")))

	val landing: HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
		"<!DOCTYPE html>" + fullHtml.render))

	def jsonResponse[T: Writer](value: T): HttpResponse = HttpResponse(entity = HttpEntity(
		ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`),
		upickle.write(value)))

	def bookmarks(user: User): HttpResponse = jsonResponse(user.bookmarks)

	def collections(implicit ntx: Ntx): HttpResponse = jsonResponse(listCollections.map { c => Narration(c.id, c.name, c.size, Nil) })


	def collection(collection: Collection)(implicit ntx: Ntx) = {
		def assetList: List[Story.Asset] = {
			def innerAssets(node: Node): List[Story.Asset] = {
				Traversal.fold(List[Story.Asset](), node) { (state, node) =>
					node match {
						case Coin.isAsset(asset) => asset.story.copy(blob = asset.blob.map(b => Story.Blob(b.sha1, b.mediastring))) :: state
						case _ => state
					}
				}
			}

			Traversal.next(collection.self).fold[List[Story.Asset]](Nil)(innerAssets).reverse
		}
		jsonResponse(Narration(collection.id, collection.name, collection.size, assetList))
	}

}