package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import viscel.shared.Story
import Story.{Chapter, More}
import viscel.narration.SelectUtil._
import viscel.narration.{Narrator, Selection}

object Flipside extends Narrator {

	def archive = More("http://flipside.keenspot.com/chapters.php", "archive") :: Nil

	def id: String = "NX_Flipside"

	def name: String = "Flipside"

	def wrapArchive(doc: Document) = {
		Selection(doc).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(elementIntoPointer("page")).map { _.distinct }
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map { _.text() }
			else
				Selection(data).unique("p > b").getOne.map { _.text }

			withGood(pages_?, name_?) { (pages, name) =>
				Chapter(name) :: pages
			}
		}
	}

	def wrap(doc: Document, pd: More): List[Story] = storyFromOr(pd.pagetype match {
		case "archive" => wrapArchive(doc)
		case "page" => Selection(doc).unique("img.ksc").wrapEach(imgIntoAsset)
	})
}