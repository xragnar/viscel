package viscel.crawler

import com.typesafe.scalalogging.slf4j.StrictLogging
import rescala.events.{Event, ImperativeEvent}
import spray.client.pipelining._
import viscel.narration.Narrator
import viscel.store._
import viscel.store.coin.Collection

import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object Clockwork extends StrictLogging {

	val jobs: concurrent.Map[String, Job] = concurrent.TrieMap[String, Job]()

	val archiveHint = new ImperativeEvent[StoryCoin]()
	val collectionHint = new ImperativeEvent[Collection]()

	val hints: Event[Collection] = archiveHint.map(_.collection) || collectionHint

	def ensureJob(core: Narrator, collection: Collection, ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = {
		val job = new Job(core, neo, iopipe, ec)
		jobs.putIfAbsent(core.id, job) match {
			case Some(x) => logger.info(s"$core race on job creation")
			case None =>
				logger.info(s"add new job $job")
				job.start(collection).onComplete {
					case Success(_) => logger.info(s"$job completed successfully")
					case Failure(t) => logger.warn(s"$job failed with $t")
				}(ec)
		}
	}

	def handleHints(ec: ExecutionContext, iopipe: SendReceive, neo: Neo): Unit = hints += { collection =>
		val id = neo.txs { collection.id }
		if (jobs.contains(id)) logger.trace(s"$id has running job")
		else Future {
			logger.info(s"got hint $id")
			Narrator.get(id) match {
				case None => logger.warn(s"unkonwn core $id")
				case Some(core) => ensureJob(core, collection, ec, iopipe, neo)
			}
		}(ec)
	}

}