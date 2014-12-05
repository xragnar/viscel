package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation.withGood
import org.scalactic.{ErrorMessage, Every, Or}
import viscel.narration.SelectUtil.{elementIntoPointer, imgIntoAsset, selectNext, storyFromOr}
import viscel.narration.{Narrator, Selection}
import viscel.shared.Story.{Chapter, More}
import viscel.shared.{AbsUri, Story}

import scala.collection.immutable.Set

object Batoto {

	case class Generic(id: String, name: String, first: AbsUri) extends Narrator {

		def archive = More(first, "chapter") :: Nil

		def wrapPage(doc: Document): Or[List[Story], Every[ErrorMessage]] =
			Selection(doc).unique("#comic_page").wrapEach(imgIntoAsset)

		def wrapChapter(doc: Document): Or[List[Story], Every[ErrorMessage]] = {
			val pages_? = Selection(doc).first("#page_select").many("option:not([selected=selected])").wrapEach { elementIntoPointer("page") }
			val currentPage_? = wrapPage(doc)
			val nextChapter_? = Selection(doc).first(".moderation_bar").optional("a:has(img[title=Next Chapter])").wrap(selectNext("chapter"))
			val chapter_? = Selection(doc).first("select[name=chapter_select]").unique("option[selected=selected]").getOne.map(e => Chapter(e.text) :: Nil)
			withGood(chapter_?, currentPage_?, pages_?, nextChapter_?) { _ ::: _ ::: _ ::: _ }
		}

		def wrap(doc: Document, kind: String): List[Story] = storyFromOr(kind match {
			case "page" => wrapPage(doc)
			case "chapter" => wrapChapter(doc)
		})
	}


	val cores: Set[Narrator] = Set(
		Generic("Batoto_mangaka-san-to-assistant-san-to-2", "Mangaka-san to Assistant-san to 2", "http://bato.to/read/_/185677/mangaka-san-to-assistant-san-to-2_ch1_by_madman-scans"),
		Generic("Batoto_kimi-no-iru-machi", "Kimi no Iru Machi", "http://bato.to/read/_/46885/kimi-no-iru-machi_v1_ch1_by_red-hawk-scans"),
		Generic("Batoto_nisekoi", "Nisekoi", "http://bato.to/read/_/20464/nisekoi_by_cxc-scans"),
		Generic("Batoto_bokura-wa-minna-kawaisou", "Bokura wa Minna Kawaisou", "http://bato.to/read/_/253606/bokura-wa-minna-kawaisou_v1_ch1_by_kuudere-scans"),
		Generic("Batoto_sankarea", "Sankarea", "http://bato.to/read/_/2015/sankarea_v1_ch1_by_milky-translation"),
		Generic("Batoto_suzuka", "Suzuka", "http://bato.to/read/_/189133/suzuka_v1_by_anime-waves"),
		Generic("Batoto_kiss-x-sis", "Kiss x Sis", "http://bato.to/read/_/8293/kiss-x-sis_by_lemon-inc/1"),
		Generic("Batoto_fuuka", "Fuuka", "http://bato.to/read/_/270324/fuuka_by_red-hawk-scans"),
		Generic("Batoto_princess-lucia", "Princess Lucia", "http://bato.to/read/_/275/princess-lucia_v1_by_red-hawk-scans"),
		Generic("Batoto_half-half", "Half and Half", "http://bato.to/read/_/130232/half-half_ch1_by_red-hawk-scans"),
		Generic("Batoto_love-letter-seo-kouji", "Love Letter (SEO Kouji)", "http://bato.to/read/_/30376/love-letter-seo-kouji_ch1_by_red-hawk-scans"),
		Generic("Batoto_loveplus-rinko-days", "LovePlus - Rinko Days", "http://bato.to/read/_/14567/loveplus-rinko-days_by_red-hawk-scans"),
		Generic("Batoto_ws-doubles", "W's - Doubles", "http://bato.to/read/_/122380/ws-doubles_v1_ch1_by_manga-fiends"),
		Generic("Batoto_cross-over", "Cross Over", "http://bato.to/read/_/87120/cross-over_v1_by_scx-scans")
	)

	//	def getCore(desc: CoreDescription): Core = Generic(id = desc.id, name = desc.name, archiveUri = desc.metadata("start"))
	//
	//	object MetaCore extends Core {
	//		override def id: String = "Meta_MangaHere"
	//		override def name: String = "Metacore MangaHere"
	//		override def archive: List[Description] = Pointer("http://www.mangahere.co/mangalist/", "") :: Nil
	//		override def wrap(doc: Document, pd: Pointer): List[Description] = Description.fromOr(
	//			Selection(doc).many("a.manga_info").wrapEach { anchor =>
	//				val name = anchor.attr("rel")
	//				val uri_? = extractUri(anchor)
	//				val id_? = uri_?.flatMap { uri => Predef.wrapString("""manga/(\w+)/""").r.findFirstMatchIn(uri.toString)
	//					.fold(Bad(One("match error")): String Or One[ErrorMessage])(m => Good(m.group(1)))
	//				}
	//				withGood(uri_?, id_?) { (uri, id) =>
	//					CoreDescription("MangaHere", s"MangaHere_$id", name, Map("start" -> uri.toString))
	//				}
	//			})
	//	}

}
