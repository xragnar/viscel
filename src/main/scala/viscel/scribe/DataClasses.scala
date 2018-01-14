package viscel.scribe

import java.time.Instant

import cats.syntax.either._
import io.circe.export.Exported
import io.circe.generic.extras.auto._
import io.circe.generic.extras._
import io.circe.{Decoder, Encoder, Json => cJson}
import viscel.shared.Blob

/** Single row in a [[Scribe]] [[Book]]. Is either a [[ScribePage]] or a [[ScribeBlob]]. */
sealed trait ScribeDataRow {
	/** reference that spawned this entry */
	def ref: Vurl
	def matchesRef(o: ScribeDataRow): Boolean = ref == o.ref
	def differentContent(o: ScribeDataRow): Boolean = (this, o) match {
		case (ScribePage(ref1, loc1, _, contents1), ScribePage(ref2, loc2, _, contents2)) =>
			!(ref1 == ref2 && loc1 == loc2 && contents1 == contents2)
		case (ScribeBlob(ref1, loc1, _, blob1), ScribeBlob(ref2, loc2, _, blob2)) =>
			!(ref1 == ref2 && loc1 == loc2 && blob1 == blob2)
		case _ => true
	}
}

/** A web page parsed and stored in [[Scribe]]
	*
	* @param ref reference that spawned this entry
	* @param loc location that was finally resolved and downloaded
	* @param date last modified timestamp when available, current date otherwise
	* @param contents links and images found on this page
	*/
/*@key("Page")*/ case class ScribePage(
	ref: Vurl,
	loc: Vurl,
	date: Instant,
	contents: List[WebContent]
) extends ScribeDataRow {
	def articleCount: Int = contents.count(_.isInstanceOf[ArticleRef])
}

/** A reference to a binary object stored in [[Scribe]]
	*
	* @param ref reference that spawned this entry, linked to [[ArticleRef.ref]]
	* @param loc location that was finally resolved and downloaded
	* @param date last modified timestamp when available, current date otherwise
	* @param blob reference to the file
	*/
/*@key("Blob")*/ case class ScribeBlob(
	/* reference that spawned this entry */
	ref: Vurl,
	/* location that was finally resolved and downloaded */
	loc: Vurl,
	date: Instant,
	blob: Blob
) extends ScribeDataRow

/** The things a person is interested in [[Chapter]] and [[Article]], content of [[Book.pages]] */
sealed trait ReadableContent

/** Aggregate the [[article]] and the [[blob]], returned  */
case class Article(article: ArticleRef, blob: Option[Blob]) extends ReadableContent

/** Result of parsing web pages by [[viscel.narration.Narrator]] */
sealed trait WebContent
/** A chapter named [[name]] */
/*@key("Chapter")*/ case class Chapter(name: String) extends WebContent with ReadableContent
/** A reference to an image or similar at url [[ref]] (referring to [[ScribeBlob.ref]])
	* and originating at [[origin]] (referring to [[ScribePage.ref]]))
	* with additional [[data]] such as HMTL attributes. */
/*@key("Article")*/ case class ArticleRef(ref: Vurl, origin: Vurl, data: Map[String, String] = Map()) extends WebContent
/** [[Link.ref]] to another [[ScribePage]], with an update [[policy]], and narator specific [[data]]. */
/*@key("Link")*/ case class Link(ref: Vurl, policy: Policy = Normal, data: List[String] = Nil) extends WebContent

/** The update [[Policy]] decides if [[viscel.crawl.Crawl]] checks for updates [[Volatile]] or not [[Normal]] */
sealed trait Policy
/*@key("Normal")*/ case object Normal extends Policy
/*@key("Volatile")*/ case object Volatile extends Policy


/** Pickler customization for compatibility */
object ScribePicklers {
	private def makeIntellijBelieveTheImportIsUsed: Exported[Decoder[Policy]] = exportDecoder[Policy]

	/** use "\$type" field in json to detect type, was upickle default and is used every [[Book]] ... */
	implicit val config: Configuration = Configuration.default.withDefaults.withDiscriminator("$" + "type")

	/** rename [[ArticleRef]] to just "Article" in the serialized format */
	implicit val webContentReader: Decoder[WebContent] = semiauto.deriveDecoder[WebContent].prepare{ cursor =>
		val t = cursor.downField("$type")
		t.as[String] match {
			case Right("Article") => t.set(io.circe.Json.fromString("ArticleRef")).up
			case _ => cursor
		}
	}
	implicit val webContentWriter: Encoder[WebContent] = semiauto.deriveEncoder[WebContent].mapJson{js =>
		js.hcursor.get[String]("$type") match {
			case Right("ArticleRef") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Article")))
			case _ => js
		}
	}


	/** rename [[ScribeBlob]] and [[ScribePage]] to just "Page" and "Blob" in the serialized format */
	implicit val appendlogReader: Decoder[ScribeDataRow] = semiauto.deriveDecoder[ScribeDataRow].prepare{ cursor =>
		val t = cursor.downField("$type")
		t.as[String] match {
			case Right("Blob") => t.set(io.circe.Json.fromString("ScribeBlob")).up
			case Right("Page") => t.set(io.circe.Json.fromString("ScribePage")).up
			case _ => cursor
		}
	}
	implicit val appendLogWriter: Encoder[ScribeDataRow] = semiauto.deriveEncoder[ScribeDataRow].mapJson{js =>
		js.hcursor.get[String]("$type") match {
			case Right("ScribeBlob") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Blob")))
			case Right("ScribePage") => js.deepMerge(cJson.obj("$type" -> cJson.fromString("Page")))
			case _ => js
		}
	}


	/** coding for instants saved to the database */
	implicit val instantWriter: Encoder[Instant] = Encoder.encodeString.contramap[Instant](_.toString)
	implicit val instantReader: Decoder[Instant] = Decoder.decodeString.emap { str =>
		Either.catchNonFatal(Instant.parse(str)).leftMap(t => "Instant: " + t.getMessage)
	}

}
