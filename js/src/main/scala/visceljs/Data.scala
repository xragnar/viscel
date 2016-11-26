package visceljs

import viscel.shared.{ImageRef, Contents, Description, Gallery}

case class Data(description: Description, content: Contents, bookmark: Int, fitType: Int = 2) {
	def id: String = description.id
	def pos: Int = content.gallery.pos
	def gallery: Gallery[ImageRef] = content.gallery
	def move(f: Gallery[ImageRef] => Gallery[ImageRef]): Data = copy(content = content.copy(gallery = f(gallery)))
	def next: Data = move(_.next(1))
	def prev: Data = move(_.prev(1))
}
