package viscel.neoadapter

import org.neo4j.graphdb.Node
import Implicits.NodeOps

final case class Book(self: Node) extends AnyVal {

	def id(implicit ntx: Ntx): String = self.prop[String]("id")
	def name(implicit ntx: Ntx): String = self.get[String]("name").getOrElse(id)

}