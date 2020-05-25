package visceljs

import loci.registry.Registry
import org.scalajs.dom
import org.scalajs.dom.experimental.{Fetch, HttpMethod, RequestInit}
import rescala.default._
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import visceljs.connection.{BookmarkManager, ContentConnectionManager, ServiceWorker}
import visceljs.render.{Front, Index, View}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

case class MetaInfo(version: String, remoteVersion: Signal[String], serviceState: Signal[String], connection: Signal[Int], reconnecting: Signal[Int])

object ViscelJS {

  val baseurl = ""

  def fetchtext(endpoint: String,
                method: HttpMethod = HttpMethod.GET,
                body: Option[String] = None): Future[String] = {

    val ri = js.Dynamic.literal(method = method).asInstanceOf[RequestInit]

    body.foreach { content =>
      ri.body = content
      ri.headers = js.Dictionary("Content-Type" -> "application/json;charset=utf-8")
    }

    //authentication.foreach{ user =>
    //  if (js.isUndefined(ri.headers)) ri.headers = js.Dictionary.empty[String]
    //  ri.headers.asInstanceOf[js.Dictionary[String]]("Authorization") = s"Token ${user.token}"
    //}

    Fetch.fetch(baseurl + endpoint, ri).toFuture.flatMap(_.text().toFuture)
  }


  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render


    //val start = System.currentTimeMillis()
    //
    //fetchtext("db4/NX_ElGoonishShive").map{text =>
    //  val it = text.linesIterator
    //  val name = it.next()
    //  println(name)
    //  it.map { data =>
    //    upickle.default.read[DataRow](data)(UpickleCodecs.DataRowRw)
    //  }.toList
    //}.onComplete{
    //  case Success(value) =>
    //    println(s"took ${System.currentTimeMillis - start}ms")
    //    println(s"yay $value")
    //  case Failure(exception) =>
    //    println(s"took ${System.currentTimeMillis - start}ms")
    //    println(exception)
    //}


    val swstate = ServiceWorker.register()



    val registry = new Registry

    val bookmarkManager = new BookmarkManager(registry)
    val ccm             = new ContentConnectionManager(registry)
    ccm.autoreconnect()

    val actions = new Actions(ccm, bookmarkManager)

    val meta = MetaInfo(viscel.shared.Version.str, ccm.remoteVersion, swstate, ccm.connectionStatus, ccm.reconnecting)


    val index   = new Index(meta, actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front   = new Front(actions)
    val view    = new View(actions)
    val app     = new ReaderApp(content = ccm.content,
                                descriptions = ccm.descriptions,
                                bookmarks = bookmarkManager.bookmarks
                                )

    val bodySig        = app.makeBody(index, front, view)
    val safeBodySignal = bodySig

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    safeBodySignal.asModifier.applyTo(bodyParent)

  }
}
