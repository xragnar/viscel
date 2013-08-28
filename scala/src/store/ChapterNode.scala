package viscel.store

import com.typesafe.scalalogging.slf4j.Logging
import org.neo4j.cypher.ExecutionEngine
import org.neo4j.graphdb.DynamicLabel
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.Direction
import scala.collection.JavaConversions._
import util.Try
import viscel.time

class ChapterNode(val self: Node) extends {
	val selfLabel = label.Chapter
} with NodeContainer[ElementNode] with ContainableNode[ChapterNode] with ViscelNode {

	def containRelation = rel.chapter
	def makeChild = ElementNode(_)
	def makeSelf = ChapterNode(_)

	def name = Neo.txs { self.get[String]("name") }

	def delete: Unit = {}

}

object ChapterNode {
	def apply(node: Node) = new ChapterNode(node)
	def apply(id: String) = Neo.node(label.Chapter, "id", id).map { new ChapterNode(_) }

	def create(name: String) = ChapterNode(Neo.create(label.Chapter, "name" -> name))
}