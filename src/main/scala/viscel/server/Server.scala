package viscel.server

import akka.actor.{Actor, ActorRefFactory}
import akka.pattern.ask
import spray.can.Http
import spray.can.server.Stats
import spray.http.{ContentType, MediaTypes}
import spray.routing.{HttpService, Route}
import viscel.narration.{Metarrators, Narrators}
import viscel.scribe.Scribe
import viscel.scribe.database.{Books, Neo}
import viscel.store.User
import viscel.{Deeds, Log, ReplUtil}

import scala.Predef.{$conforms, ArrowAssoc}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


class Server(scribe: Scribe) extends Actor with HttpService {

	implicit def neo: Neo = scribe.neo
	def books: Books = scribe.books
	val pages = new ServerPages(scribe)

	import scribe.blobs.hashToPath

	def actorRefFactory: ActorRefFactory = context

	val users = new UserStore

	override def receive: Receive = runRoute {
		//(encodeResponse(Gzip) | encodeResponse(Deflate) | encodeResponse(NoEncoding)) {
		authenticate(users.loginOrCreate) { user => defaultRoute(user) }
		//}
	}

	// we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
	implicit def implicitExecutionContext: ExecutionContextExecutor = actorRefFactory.dispatcher


	def handleBookmarksForm(user: User)(continue: User => Route): Route =
		formFields(('narration.?.as[Option[String]], 'bookmark.?.as[Option[Int]])) { (colidOption, bmposOption) =>
			val newUser = for (bmpos <- bmposOption; colid <- colidOption) yield {
				if (bmpos > 0) users.userUpdate(user.setBookmark(colid, bmpos))
				else users.userUpdate(user.removeBookmark(colid))
			}
			continue(newUser.getOrElse(user))
		}

	def defaultRoute(user: User): Route =
		path("") {
			complete(pages.landing)
		} ~
			path("stop") {
				if (!user.admin) reject
				else complete {
					Future {
						spray.util.actorSystem.shutdown()
						scribe.neo.shutdown()
						Log.info("shutdown complete")
					}
					"shutdown"
				}
			} ~
			path("css") {
				getFromResource("style.css")
			} ~
			path("js") {
				getFromFile("js/target/scala-2.11/viscel-js-opt.js") ~ getFromFile("js/target/scala-2.11/viscel-js-fastopt.js") ~
					getFromResource("viscel-js-opt.js") ~ getFromResource("viscel-js-fastopt.js")
			} ~
			path("viscel-js-fastopt.js.map") {
				getFromFile("js/target/scala-2.11/viscel-js-fastopt.js.map")
			} ~
			path("viscel-js-opt.js.map") {
				getFromFile("js/target/scala-2.11/viscel-js-opt.js.map")
			} ~
			path("bookmarks") {
				handleBookmarksForm(user)(newUser => complete(pages.bookmarks(newUser)))
			} ~
			path("narrations") {
				complete(pages.narrations())
			} ~
			path("narration" / Segment) { collectionId =>
				rejectNone(pages.narration(collectionId)) { content =>
					complete(pages.jsonResponse(content))
				}
			} ~
			pathPrefix("blob" / Segment) { (sha1) =>
				val filename = hashToPath(sha1).toFile
				pathEnd {getFromFile(filename)} ~
					path(Segment / Segment) { (part1, part2) =>
						getFromFile(filename, ContentType(MediaTypes.getForKey(part1 -> part2).get))
					}
			} ~
			pathPrefix("hint") {
				path("narrator" / Segment) { narratorID =>
					rejectNone(Narrators.get(narratorID)) { nar =>
						parameters('force.?.as[Option[Boolean]]) { force =>
							complete {
								Deeds.narratorHint(nar, force.getOrElse(false))
								force.toString
							}
						}
					}
				}
			} ~
			path("stats") {
				complete {
					val stats = actorRefFactory.actorSelection("/user/IO-HTTP/listener-0")
						.ask(Http.GetStats)(1.second)
						.mapTo[Stats]
					stats map pages.stats
				}
			} ~
			path("export" / Segment) { (id) =>
				if (!user.admin) reject
				else onComplete(Future(new ReplUtil(scribe).export(id))) {
					case Success(v) => complete("success")
					case Failure(e) => complete(e.toString())
				}
			} ~
			path("import" / Segment) { (id) =>
				if (!user.admin) reject
				else parameters('name.as[String], 'path.as[String]) { (name, path) =>
					onComplete(Future(new ReplUtil(scribe).importFolder(path, s"Import_$id", name))) {
						case Success(v) => complete("success")
						case Failure(e) => complete(e.toString())
					}
				}
			} ~
			path("add") {
				if (!user.admin) reject
				else parameter('url.as[String]) { url =>
					onComplete(Metarrators.add(url, scribe)) {
						case Success(v) => complete(s"found ${v}")
						case Failure(e) => complete {e.getMessage}
					}
				}
			} ~
			path("reload") {
				if (!user.admin) reject
				else complete {
					Narrators.update()
					"done"
				}
			} ~
			path("purge" / Segment) { (id) =>
				if (!user.admin) reject
				else onComplete(Future(scribe.purge(id))) {
					case Success(b) => complete(s"$b")
					case Failure(e) => complete(e.toString())
				}
			}

	def rejectNone[T](opt: => Option[T])(route: T => Route) = opt.map {route}.getOrElse(reject)
}
