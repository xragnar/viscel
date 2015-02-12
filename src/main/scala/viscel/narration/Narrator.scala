package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.{Or, Every}
import org.scalactic.TypeCheckedTripleEquals._
import viscel.shared.{More, Story}

trait Problem

trait Narrator {
	def id: String
	def name: String
	def archive: List[Story]
	def wrap(doc: Document, more: More): List[Story] Or Every[Problem]
	final override def equals(other: Any) = other match {
		case o: Narrator => id === o.id
		case _ => false
	}
	final override def hashCode: Int = id.hashCode
	override def toString: String = s"$id($name)"
}


