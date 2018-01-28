package viscel.crawl

import java.nio.file.Path
import java.util.{Timer, TimerTask}

import viscel.narration.Narrator
import viscel.scribe.Scribe
import viscel.shared.Log
import viscel.store.{Json, NarratorCache, Users}

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class Clockwork(
	path: Path,
	scribe: Scribe,
	ec: ExecutionContext,
	requestUtil: RequestUtil,
	userStore: Users,
	narratorCache: NarratorCache,
) {

	val dayInMillis: Long = 24L * 60L * 60L * 1000L


	val timer: Timer = new Timer(true)
	val delay: Long = 0L
	val period: Long = 60L * 60L * 1000L // every hour

	var running: Map[String, Crawl] = Map()

	def recheckPeriodically(): Unit = {
		timer.scheduleAtFixedRate(new TimerTask {
			override def run(): Unit = synchronized {
				Log.info("schedule updates")
				val narrators = userStore.allBookmarks().flatMap(narratorCache.get)
				narrators.foreach {runNarrator(_, 7 * dayInMillis)}
			}
		}, delay, period)
	}

	def runNarrator(n: Narrator, recheckInterval: Long) = synchronized {
		if (needsRecheck(n.id, recheckInterval) && !running.contains(n.id)) {
			val crawl = new Crawl(n, scribe, requestUtil)(ec)
			running = running.updated(n.id, crawl)
			implicit val iec: ExecutionContext = ec
			crawl.start().andThen{ case _ => Clockwork.this.synchronized(running = running - n.id) }.onComplete {
				case Failure(RequestException(request, response)) =>
					Log.error(s"[${n.id}] error request: ${request.uri} failed: ${response.status}")
				case Failure(t) =>
					Log.error(s"[${n.id}] recheck failed with $t")
					t.printStackTrace()
				case Success(true) =>
					Log.info(s"[${n.id}] update complete")
					updateDates(n.id)
				case Success(false) =>
			}
		}
	}

	private var updateTimes: Map[String, Long] = Json.load[Map[String, Long]](path).fold(x => x, err => {
		Log.error(s"could not load $path: $err")
		Map()
	})

	def updateDates(id: String): Unit = synchronized {
		val time = System.currentTimeMillis()
		updateTimes = updateTimes.updated(id, time)
		Json.store(path, updateTimes)
	}

	def needsRecheck(id: String, recheckInterval: Long): Boolean = synchronized {
		val lastRun = updateTimes.get(id)
		val time = System.currentTimeMillis()
		val res = lastRun.isEmpty || (time - lastRun.get > recheckInterval)
		Log.trace(s"calculating recheck for $id: $res")
		res
	}

}
