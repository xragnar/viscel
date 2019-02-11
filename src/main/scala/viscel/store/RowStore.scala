package viscel.store

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import better.files.File
import io.circe.syntax._
import viscel.narration.Narrator
import viscel.shared.Log.{Store => Log}
import viscel.shared.Vid
import viscel.store.CustomPicklers._

class RowStore(basePath: Path) {

  val base = File(basePath)
  assert(base.isDirectory, "row store base path must be directory")

  def open(narrator: Narrator): RowAppender = synchronized {
    val f = base / narrator.id.str
    if (!f.exists || f.size <= 0) Json.store(f.path, narrator.name)
    new RowAppender(f)
  }

  def allVids(): List[Vid] = synchronized {
    base.list(_.isRegularFile, 1).map(f => Vid.from(f.name)).toList
  }


  def load(id: Vid): (String, List[ScribeDataRow]) = synchronized {
    Log.info(s"loading $id")

    val f = File(basePath)./(id.str)

    if (!f.isRegularFile || f.size == 0) throw new IllegalStateException(s"$f does not contain a book")
    else {
      val lines = f.lineIterator(StandardCharsets.UTF_8)
      val name = io.circe.parser.decode[String](lines.next()).toTry.get

      val entryList = lines.zipWithIndex.map { case (line, nr) =>
        io.circe.parser.decode[ScribeDataRow](line) match {
          case Right(s) => s
          case Left(t)  =>
            Log.error(s"Failed to decode $id:${nr + 2}: $line")
            throw t
        }
      }.toList
      (name, entryList)
    }
  }

  def loadBook(id: Vid): Book = {
    val (name, entryList) = load(id)
    Book.fromEntries(id, name, entryList)
  }

}

class RowAppender(file: File) {
  def append(row: ScribeDataRow): Unit = {
    Log.trace(s"Store $row into $file")
    file.appendLine(row.asJson.noSpaces)(charset = StandardCharsets.UTF_8)
  }
}