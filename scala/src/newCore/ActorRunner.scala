package viscel.newCore

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.JavaConversions._
import scala.Some
import spray.client.pipelining.SendReceive
import viscel.store._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.pattern.pipe
import org.jsoup.nodes.Document

class ActorRunner(val iopipe: SendReceive, val core: Core, val collection: CollectionNode) extends Actor with ArchiveManipulation with NetworkPrimitives {

	override def preStart() = Neo.txs { append(collection, core.archive) }

	def undescribed: Seq[PageNode] = ArchiveNode(collection).map { an =>
		ArchiveNode.foldChildren(Seq[PageNode](), an) {
			case (acc, pn: PageNode) => if (pn.describes.isEmpty) acc :+ pn else acc
			case (acc, sn: StructureNode) => acc
		}
	}.getOrElse(Seq[PageNode]())

	def next() = Neo.txs {
		undescribed.headOption match {
			case Some(pn) =>
				getDocument(pn.location).map { pn -> _ }.pipeTo(self)
			case None => self ! "done"
		}
	}

	def always: Receive = {
		case (pn: PageNode, doc: Document) =>
			Neo.txs {
				append(pn, core.wrap(doc, pn.pointerDescription))
				createLinkage(ArchiveNode(collection).get, collection)
			}
			self ! "next"
		case other => logger.warn(s"unknown message $other")
	}

	def idle: Receive = {
		case "start" =>
			context.become(running orElse always)
			next()
	}

	def running: Receive = {
		case "stop" => context.become(idle orElse always)
		case "next" => next()
	}

	def receive: Actor.Receive = idle orElse always
}

/*

val (sys, ioHttp, _) = Viscel.run("--nocore")

implicit val timeout: Timeout = 30.seconds
val iop = sendReceive(ioHttp)

import viscel.newCore._

val col = CollectionNode.create("MisfileTest", "MisfileTest")

val props = Props(classOf[ActorRunner], iop, Misfile, col)

val runner = sys.actorOf(props)

*/ 