package viscel.shared

import upickle.{Js, Reader => R, Writer => W, writeJs, readJs}
import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map

object JsonCodecs extends viscel.generated.UpickleCodecs {

	implicit def stringMapR[V: R]: R[Map[String, V]] = R[Map[String, V]] {
		case Js.Obj(kv @ _*) => kv.map{case (k, jsv) => k -> readJs[V](jsv)}.toMap
	}
	implicit def stringMapW[V: W]: W[Map[String, V]] = W[Map[String, V]] { m =>
		Js.Obj(m.mapValues(writeJs[V]).toSeq: _*)
	}

}
