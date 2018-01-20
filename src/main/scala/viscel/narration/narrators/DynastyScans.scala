package viscel.narration.narrators

import org.jsoup.helper.StringUtil
import viscel.narration.Narrator
import viscel.narration.Queries.queryChapterArchive
import viscel.narration.Templates.ArchivePage
import viscel.scribe.ArticleRef
import viscel.selection.ReportTools.extract
import viscel.selection.Selection

import scala.util.matching.Regex

object DynastyScans {

	val pages: Regex = """/system/releases/\d+/\d+/\d+/[^"]+""".r

	val Citrus: Narrator = ArchivePage(
		pid = "DS_citrus",
		pname = "Citrus",
		start = "https://dynasty-scans.com/series/citrus",
		wrapArchive = queryChapterArchive(s".chapter-list a.name:not(:contains(love panic))"),
		wrapPage = doc => Selection(doc).unique("body > script").wrapFlat { script =>
			extract {
				pages.findAllIn(script.html()).map { url =>
					ArticleRef(StringUtil.resolve(doc.baseUri(),url), doc.location())
				}.toList
			}
		}
	)

}
