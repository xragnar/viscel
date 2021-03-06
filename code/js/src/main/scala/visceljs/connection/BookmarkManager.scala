package visceljs.connection

import loci.registry.Registry
import rescala.default._
import rescala.extra.distributables.LociDist
import rescala.extra.lattices.Lattice
import viscel.shared.JsoniterCodecs._
import viscel.shared.{Bindings, Bookmark, BookmarksMap, Vid}
import visceljs.storage.Storing

class BookmarkManager(registry: Registry) {
  val setBookmark = Evt[(Vid, Bookmark)]()
  val bookmarks =
    Storing.storedAs("bookmarksmap", Map.empty[Vid, Bookmark]) { initial =>
      setBookmark.fold(initial) {
        case (map, (vid, bm)) =>
          Lattice.merge(map, BookmarksMap.addΔ(vid, bm))
      }
    }

  LociDist.distribute(bookmarks, registry)(Bindings.bookmarksMapBindig)

}
