package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic._
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story
import viscel.shared.Story.{Chapter, More}


object Twokinds extends Narrator {

	def archive = More("http://twokinds.keenspot.com/?p=archive", "archive") :: More("http://twokinds.keenspot.com/index.php", "main") :: Nil

	def id: String = "NX_Twokinds"

	def name: String = "Twokinds"

	def wrapArchive(doc: Document): List[Story] Or Every[ErrorMessage] = {
		val chapters_? = Selection(doc).many(".archive .chapter").wrapEach { chapter =>
			val title_? = Selection(chapter).unique("h4").getOne.map(_.ownText())
			val links_? = Selection(chapter).many("a").wrapEach { elementIntoPointer("page") }
			withGood(title_?, links_?) { (title, links) =>
				Chapter(title) :: links
			}
		}
		chapters_?.map(_.flatten(Predef.$conforms))
	}

	def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
		case "archive" => wrapArchive(doc)
		case "page" => Selection(doc).unique("#cg_img img").wrapEach { imgIntoAsset }
		case "main" => Selection(doc).unique(".comic img").wrapEach { imgIntoAsset }
	})
}
