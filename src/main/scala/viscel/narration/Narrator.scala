package viscel.narration

import org.jsoup.nodes.Document
import org.scalactic.TypeCheckedTripleEquals._
import viscel.narration.interpretation.NarrationInterpretation
import viscel.narration.interpretation.NarrationInterpretation.Wrapper
import viscel.scribe.{Link, WebContent}
import viscel.shared.Vid

/** Describes the structure of a web collection */
trait Narrator {
  /** [[id]] of each [[Narrator]] is globally unique,
    * and used to lookup the [[Narrator]] and the result in all data structures.
    * Typicall something like XX_WonderfulCollection where XX is some grouping string,
    * and WonderfulCollection the normalized [[name]] of the collection. */
  def id: Vid
  /** name of the collection */
  def name: String

  /** Starting link, or multiple links in case the structure is very static */
  def archive: List[WebContent]

  /** Wraps a [[Document]] into [[Contents]]
    *
    * @param doc  root document returned when downloading link
    * @param link link that describes the doc
    * @return list of all the contents of the document, or all error messages during parsing
    */
  def wrap(doc: Document, link: Link): Contents

  def wrapper: Wrapper = NarrationInterpretation.OmnipotentWrapper(wrap)

  final override def equals(other: Any): Boolean = other match {
    case o: Narrator => id === o.id
    case _ => false
  }
  final override def hashCode: Int = id.hashCode
  override def toString: String = s"$id($name)"
}


