package viscel.narration

import viscel.selektiv.Narration.PolicyDecision
import viscel.narration.Narrator.Wrapper
import viscel.shared.Vid
import viscel.store.v3.Volatile
import viscel.store.v4.{DataRow, Vurl}

object Templates {
  def archivePage(vid: String,
                  pname: String,
                  start: Vurl,
                  wrapArchive: Wrapper,
                  wrapPage: Wrapper,
                 ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, DataRow.Link(start, List(Volatile.toString)) :: Nil,
      PolicyDecision(
        volatile = wrapArchive,
        normal = wrapPage))

  def SimpleForward(vid: String,
                    pname: String,
                    start: Vurl,
                    wrapPage: Wrapper
                   ): NarratorADT =
    NarratorADT(Vid.from(vid), pname, DataRow.Link(start) :: Nil, wrapPage)
}
