package visceljs

import loci.communicator.ws.akka.WS
import loci.registry.Registry
import loci.transmitter.RemoteRef
import org.scalajs.dom
import org.scalajs.dom.experimental.{RequestInfo, URL}
import org.scalajs.dom.experimental.serviceworkers.CacheStorage
import org.scalajs.dom.raw.Storage
import rescala.default._
import rescala.extra.distributables.LociDist
import rescala.extra.lattices.IdUtil
import rescala.extra.lattices.IdUtil.Id
import rescala.extra.lattices.Lattice
import rescala.extra.restoration.ReCirce
import scalatags.JsDom.implicits.stringFrag
import scalatags.JsDom.tags.body
import viscel.shared.Bindings.SetBookmark
import viscel.shared.BookmarksMap._
import viscel.shared._
import visceljs.render.{Front, Index, View}

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Failure, Success, Try}


class BookmarkManager(registry: Registry) {
  val setBookmark      = Evt[(Vid, Bookmark)]
  val bookmarks    = {
    val storage: Storage = dom.window.localStorage
    val bmms = ReCirce.recirce[BookmarksMap]
    val key = "bookmarksmap"
    val bmm = Try(Option(storage.getItem(key)).get).flatMap{ str =>
      bmms.deserialize(str)
    }.getOrElse(Map.empty)
    val bmCRDT = setBookmark.fold(bmm){ case (map, (vid, bm)) =>
      Lattice.merge(map, BookmarksMap.addΔ(vid, bm))
    }
    bmCRDT.observe{ v =>
      storage.setItem(key, bmms.serialize(v))
    }
    bmCRDT
  }

  LociDist.distribute(bookmarks, registry)(Bindings.bookmarksMapBindig)

  val postBookmarkF: SetBookmark => Unit = { set: Bindings.SetBookmark =>
    set.foreach{ bm =>
      setBookmark.fire(bm._1.id -> bm._2)
    }
  }
}


class ContentConnectionManager(registry: Registry) {
  import scala.collection.mutable
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private val contents: scala.collection.mutable.Map[Vid, Signal[Contents]] = mutable.Map()

  val descriptions = Var.empty[Map[Vid, Description]]
  val wsUri: String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}${dom.document.location.pathname}ws"
  }

  def connect(): Unit = {
    Log.JS.info(s"trying to connect to $wsUri")
    val connection: Future[RemoteRef] = registry.connect(WS(wsUri))
    connection.flatMap(updateDescriptions)
    connection.failed.foreach(_ => connect())
  }
  registry.remoteLeft.foreach(_ => connect())
  connect()

  def content(nar: Description): Signal[Contents] = {
    hint(nar, force = false)
    contents.getOrElseUpdate(nar.id, {
      registry.remotes.find(_.connected).map { remote =>
        val requestContents  = registry.lookup(Bindings.contents, remote)
        val emptyContents = Contents(Gallery.empty, Nil)
        val eventualContents = requestContents(nar.id).map(_.getOrElse(emptyContents))
        eventualContents.onComplete(t => Log.JS.debug(s"received contents for ${nar.id} (sucessful: ${t.isSuccess})"))
        eventualContents.foreach{contents =>
          Log.JS.info(s"prefetching ${nar.id} ")
          def toUrl(blob: Blob) = new URL(Definitions.path_blob(blob), dom.document.location.href)
          val urls = contents.gallery.toSeq.iterator.flatMap(_.blob).map(toUrl)
          val caches = dom.window.asInstanceOf[js.Dynamic].caches.asInstanceOf[CacheStorage]
          caches.open(s"vid${nar.id}").`then`[Unit]{ cache =>
            cache.addAll(js.Array(urls.map[RequestInfo](_.href).toSeq : _*))
          }
          //contents.gallery.toSeq.foreach{image =>
          //  Log.JS.info(image.blob.toString)
          //  image.blob.foreach { blob =>
          //    val url = new URL(Definitions.path_blob(blob), dom.document.location.href)
          //    Log.JS.info(url.href)
          //    val navigator  = dom.window.navigator
          //    Log.JS.info(s"navigator : $navigator")
          //    val controller = navigator.asInstanceOf[js.Dynamic].serviceWorker.controller
          //    Log.JS.info(s"controller : $controller")
          //    controller.postMessage(
          //      js.Dynamic.literal(
          //        "command" -> "add",
          //        "vid" -> nar.id.str,
          //        "url" -> url.href
          //        )
          //      )
          //  }
          //}
        }
        Signals.fromFuture(eventualContents).withDefault(emptyContents)
      }.getOrElse(Var.empty[Contents])
    })
  }

  def hint(descr: Description, force: Boolean): Unit = {
    registry.remotes.filter(_.connected).foreach { connection =>
      registry.lookup(Bindings.hint, connection).apply(descr, force).failed
              .foreach(e => Log.JS.error(s"sending hint failed: $e"))
    }
  }






  private def updateDescriptions(remote: RemoteRef): Future[_] = {
    val lookup = registry.lookup(Bindings.descriptions, remote).apply()
    lookup.onComplete {
      case Success(value)     =>
        val descriptionMap = value.map(n => n.id -> n).toMap
        descriptions.set(descriptionMap)
      case Failure(exception) =>
        Log.JS.error("failed to fetch descriptions")
    }
    lookup
  }
}


object ViscelJS {

  val replicaID: Id = IdUtil.genId()

  def main(args: Array[String]): Unit = {
    dom.document.body = body("loading data …").render
    val registry = new Registry

    val bookmarkManager = new BookmarkManager(registry)
    val ccm = new ContentConnectionManager(registry)

    val manualStates = Evt[AppState]()

    val actions = new Actions(hint = ccm.hint, postBookmarkF = bookmarkManager.postBookmarkF, manualStates = manualStates)
    val index = new Index(actions, bookmarkManager.bookmarks, ccm.descriptions)
    val front = new Front(actions)
    val view = new View(actions)
    val app = new ReaderApp(content = ccm.content,
                            descriptions = ccm.descriptions,
                            bookmarks = bookmarkManager.bookmarks
                            )

    val bodySig = app.makeBody(index, front, view, manualStates)
    val safeBodySignal = bodySig

    val bodyParent = dom.document.body.parentElement
    bodyParent.removeChild(dom.document.body)
    import rescala.extra.Tags._
    safeBodySignal.asModifier.applyTo(bodyParent)

  }

}
