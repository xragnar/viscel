package viscel.narration.narrators

import org.jsoup.nodes.Document
import org.scalactic.Accumulation._
import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Every, Good, Or}
import viscel.narration.Queries._
import viscel.narration.Templates.{ArchivePage, SimpleForward}
import viscel.narration.{Narrator, Templates}
import viscel.scribe.Vurl.fromString
import viscel.scribe.{ArticleRef, Chapter, Link, Normal, Volatile, Vurl, WebContent}
import viscel.selection.ReportTools._
import viscel.selection.{Report, Selection}

import scala.collection.immutable.Set

object Individual {

	object Candi extends Narrator {
		def archive = Link("http://candicomics.com/archive.html", Volatile, "archive" :: Nil) :: Nil

		def id: String = "NX_Candi"

		def name: String = "Candi"

		def wrapArchive(doc: Document): Or[List[WebContent], Every[Report]] = {
			val volumes_? = morePolicy(Volatile,
				Selection(doc).many("#candimidd > p:nth-child(2) a").wrapEach {extractMore})
			// the list of volumes is also the first volume, wrap this directly
			val firstVolume_? = wrapVolume(doc)

			withGood(firstVolume_?, volumes_?) { (first, volumes) =>
				first ::: volumes.drop(1)
			}
		}

		def wrapVolume(doc: Document): Or[List[WebContent], Every[Report]] =
			Selection(doc).many("#candimidd > table > tbody > tr > td:nth-child(2n) a").wrapFlat {elementIntoChapterPointer}


		def wrap(doc: Document, more: Link): List[WebContent] Or Every[Report] = more match {
			case Link(_, Volatile, "archive" :: Nil) => wrapArchive(doc)
			case Link(_, Volatile, Nil) => wrapVolume(doc)
			case _ => queryImageNext("#comicplace > span > img", "#comicnav a:has(img#next_day2)")(doc)
		}
	}


	val Flipside = Templates.ArchivePage("NX_Flipside", "Flipside", "http://flipside.keenspot.com/chapters.php",
		Selection(_).many("td:matches(Chapter|Intermission)").wrapFlat { data =>
			val pages_? = Selection(data).many("a").wrapEach(extractMore).map {_.distinct}
			val name_? = if (data.text.contains("Chapter"))
				Selection(data).unique("td:root > div:first-child").getOne.map {_.text()}
			else
				Selection(data).unique("p > b").getOne.map {_.text}

			withGood(pages_?, name_?) { (pages, name) =>
				Chapter(name) :: pages
			}
		},
		Selection(_).unique("img.ksc").wrapEach(intoArticle))


	object Inverloch extends Narrator {
		override def id: String = "NX_Inverloch"
		override def name: String = "Inverloch"
		override def archive: List[WebContent] = Range.inclusive(1, 5).map(i => Link(s"http://inverloch.seraph-inn.com/volume$i.html", Normal, "archive" :: Nil)).toList
		override def wrap(doc: Document, more: Link): List[WebContent] Or Every[Report] = more match {
			case Link(_, _, "archive" :: Nil) => Selection(doc).many("#main p:containsOwn(Chapter)").wrapFlat { chap =>
				cons(
					extract(Chapter(chap.ownText())),
					Selection(chap).many("a").wrapEach(extractMore))
			}
			case _ =>
				if (doc.baseUri().endsWith("summaries.html")) Good(Nil)
				else queryImageNext("#main > p:nth-child(1) > img", "#main a:containsOwn(Next)")(doc)
		}
	}

	object JayNaylor {
		def common(id: String, name: String, archiveUri: Vurl) = Templates.ArchivePage(id, name, archiveUri,
			doc => Selection(doc).many("#chapters li > a").wrapFlat {elementIntoChapterPointer},
			queryImages("#comicentry .content img"))

		def BetterDays = common("NX_BetterDays", "Better Days", "http://jaynaylor.com/betterdays/archives/chapter-1-honest-girls/")

		def OriginalLife = common("NX_OriginalLife", "Original Life", "http://jaynaylor.com/originallife/")

	}


	object Misfile extends Narrator {
		def archive = Link("http://www.misfile.com/archives.php?arc=1&displaymode=wide&", Volatile) :: Nil

		def id: String = "NX_Misfile"

		def name: String = "Misfile"

		def wrapArchive(doc: Document): Or[List[WebContent], Every[Report]] = {
			val chapters_? = Selection(doc).many("#comicbody a:matchesOwn(^Book #\\d+$)").wrapFlat { anchor =>
				extractMore(anchor).map { pointer =>
					Chapter(anchor.ownText()) :: pointer :: Nil
				}
			}
			// the list of chapters is also the first page, wrap this directly
			val firstPage_? = wrapPage(doc)

			withGood(firstPage_?, chapters_?) { (page, chapters) =>
				Chapter("Book #1") :: page ::: chapters
			}
		}

		def wrapPage(doc: Document): Or[List[WebContent], Every[Report]] = {
			if (doc.location() == "http://www.misfile.com/archives.php?arc=34&displaymode=wide") return Good(Nil)
			val elements_? = Selection(doc)
				.unique(".comiclist table.wide_gallery")
				.many("[id~=^comic_\\d+$] .picture a").wrapEach { anchor =>
				val element_? = Selection(anchor).unique("img").wrapOne {intoArticle}
				val origin_? = extractURL(anchor)
				withGood(element_?, origin_?) { (element, origin) =>
					element.copy(
						ref = element.ref.uriString.replace("/t", "/"),
						origin = origin,
						data = element.data - "width" - "height")
				}
			}
			val next_? = Selection(doc).all("a.next").wrap {selectMore}

			append(elements_?, next_?)
		}

		def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] = more match {
			case Link(_, Volatile, _) => wrapArchive(doc)
			case _ => wrapPage(doc)
		}
	}


	object NamirDeiter extends Narrator {
		override def id: String = "NX_NamirDeiter"
		override def name: String = "Namir Deiter"
		override def archive: List[WebContent] = Link(s"http://www.namirdeiter.com/archive/index.php?year=1", Volatile, "archive" :: Nil) :: Nil

		def wrapIssue(doc: Document): Or[List[WebContent], Every[Report]] = Selection(doc).many("table #arctitle > a").wrapFlat(elementIntoChapterPointer)

		override def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] = more match {
			case Link(_, Volatile, "archive" :: Nil) => append(
				wrapIssue(doc),
				morePolicy(Volatile, Selection(doc).many("body > center > div > center > h2 > a").wrapEach(extractMore)))
			case Link(_, Volatile, Nil) => wrapIssue(doc)
			case _ =>
				if (doc.baseUri() == "http://www.namirdeiter.com/comics/index.php?date=20020819") Good(Nil)
				else if (doc.baseUri() == "http://www.namirdeiter.com/") Good(Nil)
				else queryImageInAnchor("body > center > div > center:nth-child(3) > table center img")(doc)
		}
	}


	object YouSayItFirst extends Narrator {
		override def id: String = "NX_YouSayItFirst"
		override def name: String = "You Say It First"
		override def archive: List[WebContent] = Range.inclusive(1, 9).map(i => Link(s"http://www.yousayitfirst.com/archive/index.php?year=$i", data = List("archive"))).toList
		override def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] = more match {
			case Link(_, _, "archive" :: Nil) => Selection(doc).many("table #number a").wrapFlat(elementIntoChapterPointer)
			case _ =>
				if (doc.baseUri() == "http://www.yousayitfirst.com/") Good(Nil)
				else queryImageInAnchor("body > center > div.mainwindow > center:nth-child(2) > table center img")(doc)
		}
	}


	object UnlikeMinerva extends Narrator {
		override def id: String = "NX_UnlikeMinerva"
		override def name: String = "Unlike Minerva"
		override def archive: List[WebContent] = Range.inclusive(1, 25).map(i => Link(s"http://www.unlikeminerva.com/archive/phase1.php?week=$i")).toList :::
			Range.inclusive(26, 130).map(i => Link(s"http://www.unlikeminerva.com/archive/index.php?week=$i")).toList
		override def wrap(doc: Document, more: Link): Or[List[WebContent], Every[Report]] =
			Selection(doc).many("center > img[src~=http://www.unlikeminerva.com/archive/]").wrapEach { img =>
				withGood(intoArticle(img), extract(img.parent().nextElementSibling().text())) { (article, txt) =>
					article.copy(data = article.data.updated("longcomment", txt))
				}
			}
	}


	val inlineCores = Set(
		ArchivePage("NX_Twokinds", "Twokinds", "http://twokinds.keenspot.com/archive/",
			queryMixedArchive("#content .chapter h2 , #content .chapter a"),
			queryImage("#content article.comic img")
		),
		ArchivePage("NX_Fragile", "Fragile", "http://www.fragilestory.com/archive",
			doc => Selection(doc).unique("#content_post").many(".c_arch:has(div.a_2)").wrapFlat { chap =>
				val chapter_? = Selection(chap).first("div.a_2 > p").getOne.map(e => Chapter(e.text()))
				val pages_? = Selection(chap).many("a").wrapEach(extractMore)
				withGood(chapter_?, pages_?)(_ :: _)
			},
			queryImage("#comic_strip > a > img")),
		ArchivePage("NX_ElGoonishShive", "El Goonish Shive", "http://www.egscomics.com/archives.php",
			Selection(_).many("#leftarea > h3 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic")),
		ArchivePage("NX_LetsSpeakEnglish", "Let’s Speak English", "http://www.marycagle.com/archive.php",
			doc => Selection(doc).many(".cc-chapterrow a[href]").wrapFlat(elementIntoChapterPointer),
			doc => {
				val asset_? = Selection(doc).unique("#cc-comic").wrapOne(intoArticle)
				val next_? = queryNext("#cc-comicbody > a")(doc)
				val comment_? = Selection(doc).unique("#commentary > div.cc-newsarea > div.cc-newsbody").getOne.map(_.text())
				withGood(asset_?, next_?, comment_?) { (asset, next, comment) => asset.copy(data = asset.data.updated("longcomment", comment)) :: next }
			}),
		SimpleForward("NX_CliqueRefresh", "Clique Refresh", "http://cliquerefresh.com/comic/start-it-up/", queryImageInAnchor("#cc-comic")),
		ArchivePage("NX_Skullkickers", "Skull☠kickers", "http://comic.skullkickers.com/archive.php",
			Selection(_).many("#sleft > h2 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#sleft img.ksc")),
		SimpleForward("NX_StickyDillyBuns", "Sticky Dilly Buns", "http://www.stickydillybuns.com/strips-sdb/awesome_leading_man",
			doc => queryImageInAnchor("#comic img")(doc).orElse(queryNext("#cndnext")(doc))),
		SimpleForward("NX_MagicChicks", "Magic Chicks", "http://www.magickchicks.com/strips-mc/tis_but_a_trifle", queryImageInAnchor("#comic img[src~=/comics/]")),
		ArchivePage("NX_PennyAndAggie", "Penny & Aggie", "http://www.pennyandaggie.com/index.php?p=1",
			Selection(_).many("form[name=jump] > select[name=menu] > option[value]").wrapFlat(elementIntoChapterPointer),
			queryImageNext(".comicImage", "center > span.a11pixbluelinks > div.mainNav > a:has(img[src~=next_day.gif])")),
		ArchivePage("NX_MegaTokyo", "MegaTokyo", "http://megatokyo.com/archive.php",
			Selection(_).many("div.content:has(a[id~=^C-\\d+$]").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = Selection(chap).many("li a").wrapEach(extractMore)
				cons(chapter_?, elements_?)
			}, doc => {
				if (doc.location() == "http://megatokyo.com/strip/1428") Good(List(Link(Vurl.fromString("http://megatokyo.com/strip/1429"))))
				else queryImageNext("#strip img", "#comic .next a")(doc)
			}),
		SimpleForward("NX_WhatBirdsKnow", "What Birds Know", "http://fribergthorelli.com/wbk/index.php/page-1/", queryImageNext("#comic-1 img", "a.navi-next")),
		ArchivePage("NX_TodayNothingHappened", "Today Nothing Happened", "http://www.todaynothinghappened.com/archive.php",
			Selection(_).many("#wrapper > div.rant a.link").wrapEach(extractMore),
			queryImage("#comic > img")),
		ArchivePage("NX_RedString", "Red String", "http://www.redstring.strawberrycomics.com/archive.php",
			Selection(_).many("#comicwrap h2 > a").wrapFlat(elementIntoChapterPointer),
			queryImageInAnchor("#comic")),
		SimpleForward("NX_Dreamless", "Dreamless", "http://dreamless.keenspot.com/d/20090105.html",
			doc => queryImageNext("img.ksc", "a:has(#next_day1)")(doc).orElse(queryNext("a:has(#next_day1)")(doc))),
		ArchivePage("NX_PhoenixRequiem", "The Phoenix Requiem", "http://requiem.seraph-inn.com/archives.html",
			Selection(_).many("#container div.main > table tr:contains(Chapter)").wrapFlat { chap =>
				val chapter_? = extract(Chapter(chap.child(0).text()))
				val elements_? = Selection(chap).many("a").wrapEach(extractMore)
				cons(chapter_?, elements_?)
			},
			queryImage("#container img[src~=^pages/]")),
		SimpleForward("NX_ErrantStory", "Errant Story", "http://www.errantstory.com/2002-11-04/15", queryImageNext("#comic > img", "#column > div.nav > h4.nav-next > a")),
		ArchivePage("NX_StandStillStaySilent", "Stand Still Stay Silent", "http://www.sssscomic.com/?id=archive",
			queryMixedArchive("#main_text div.archivediv h2, #main_text div.archivediv a"),
			queryImage("#wrapper2 img")),
		SimpleForward("NX_Nimona", "Nimona", "http://gingerhaze.com/nimona/comic/page-1",
			queryImageNext("img[src~=/nimona-pages/]", "a:has(img[src=http://gingerhaze.com/sites/default/files/comicdrop/comicdrop_next_label_file.png])")),
		SimpleForward("NX_DominicDeegan", "Dominic Deegan", "http://www.dominic-deegan.com/view.php?date=2002-05-21",
			doc => append(queryImages("body > div.comic > img")(doc), queryNext("#bottom a:has(img[alt=Next])")(doc))),
		ArchivePage("NX_DreamScar", "dream*scar", "http://dream-scar.net/archive.php",
			Selection(_).many("#static > b , #static > a").wrapEach { elem =>
				if (elem.tagName() === "b") extract {Chapter(elem.text())}
				else extractMore(elem)
			},
			queryImage("#comic")),
		SimpleForward("NX_AvasDemon", "Ava’s Demon", "http://www.avasdemon.com/chapters.php",
			Selection(_).many("table[id~=chapter\\d+_table]").wrap {
				_.zipWithIndex.map { case (elem, idx) =>
					Selection(elem).many("a").wrap { as =>
						Good(Chapter(s"Chapter ${idx + 1}") ::
							as.sortBy(_.text()).map { a =>
								val origin = a.attr("abs:href")
								val number = a.text()
								val filename = number match {
									case "0222" => s"0243.gif"
									case "0367" => s"titanglow.gif"
									case "0368" => s"365.gif"
									case "0369" => s"366.gif"
									case "0370" => s"367.gif"
									case "0371" => s"368.gif"
									case "1799" => s"1790.png"
									case "0655" | "0762" | "1035" | "1130" | "1131" | "1132" | "1133" | "1134" |
											 "1135" | "1136" | "1271" | "1272" | "1273" | "1274" | "1293" | "1294" | "1295" | "1384" | "1466" | "1788"  => s"$number.png"
									case "0061" | "0353" | "0893" | "1546" | "1748" | "1749" | "1750" => s"$number.gif"
									case _ => s"pages/$number.png"
								}
								val source = s"http://www.avasdemon.com/$filename"
								ArticleRef(source, origin)
							})
					}
				}.combined.map(_.flatten)
			}),
		ArchivePage("NX_Spindrift", "Spindrift", "http://www.spindrift-comic.com/archive",
			queryMixedArchive("#pjax-container > div.content > div:nth-child(1) .archivehead .shead , #pjax-container > div.content > div:nth-child(1) .archive-comic-container a"),
			queryImage("#comic-image")),
		ArchivePage("NX_Anathema", "Anathema", "http://anathema.smackjeeves.com/archive//",
			queryMixedArchive("#chapter_table td[colspan=4] a h2, #chapter_table a[onmouseout=hideddrivetip()]"),
			queryImage("#comic_image")),
		SimpleForward("NX_xkcd", "xkcd", "http://xkcd.com/1/",
			doc => {
				val assets_? = Selection(doc).all("#comic img").wrapEach(intoArticle)
				val next_? = queryNext("a[rel=next]:not([href=#])")(doc)
				val assets_with_comment_? = assets_?.map(_.map { article =>
					article.data.get("title").fold(article)(t => article.copy(data = article.data.updated("longcomment", t)))
				})
				append(assets_with_comment_?, next_?)
			}),
		ArchivePage("NX_TheDreamer", "The Dreamer", "http://www.thedreamercomic.com/read_pgmain.php",
			doc => Selection(doc).many(".act_wrap").reverse.wrapFlat{queryMixedArchive("h2, .flip_box_front .issue_title , .flip_box_back .issue_pages a")},
			doc => queryImage("#comicnav > div.comicWrap > div.imageWrap > img")(doc).map(_.map{ ar =>
				ar.copy(ref = ar.ref.uriString().replaceAll("\\.jpg.*", ".jpg"))
			})
		),
		SimpleForward("NX_CheerImgur", "Cheer by Forview", "http://imgur.com/a/GTprX/",
			doc => {
				Selection(doc).unique("div.post-images").many("div.post-image-container").wrapEach{ div =>
					extract(ArticleRef(
						ref = s"http://i.imgur.com/${div.attr("id")}.png",
						origin = "http://imgur.com/a/GTprX/"))
				}
			})
	)

}
