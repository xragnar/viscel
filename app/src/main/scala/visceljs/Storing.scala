package visceljs

import org.scalajs.dom
import rescala.default.Signal
import upickle.default._
import rescala.default.implicitScheduler

object Storing {

  def storedAs[A: ReadWriter](key: String, default: => A)(create: A => Signal[A]): Signal[A] = {
    val init: A =
      try {upickle.default.read[A](dom.window.localStorage.getItem(key))}
      catch {case _: Throwable => default}
    val sig     = create(init)
    sig.observe { ft: A =>
      dom.window.localStorage.setItem(key, upickle.default.write(ft))
    }
    sig
  }

}
