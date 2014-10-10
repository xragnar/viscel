package viscel.narration

import org.scalactic.{ErrorMessage, Every, Or}
import viscel.crawler.AbsUri

import scala.collection.immutable.Map
import scala.language.implicitConversions

sealed trait Story

object Story {
	def fromOr(or: List[Story] Or Every[ErrorMessage]): List[Story] = or.fold(Predef.identity, Failed(_) :: Nil)

	final case class More(loc: AbsUri, pagetype: String) extends Story
	final case class Chapter(name: String, metadata: Map[String, String] = Map()) extends Story
	final case class Asset(source: AbsUri, origin: AbsUri, metadata: Map[String, String] = Map()) extends Story
	final case class Core(kind: String, id: String, name: String, metadata: Map[String, String]) extends Story
	final case class Failed(reason: Every[ErrorMessage]) extends Story
}