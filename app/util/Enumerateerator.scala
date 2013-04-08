package org.w3.vs.util

import play.api.libs.iteratee._

/** lets you think about Enumerator[Iterator[X]] as if it was an Enumerator[X] */
object Enumerateerator {

  def map[E] = new Object {
    def apply[NE](f: E => NE): Enumeratee[Iterator[E], Iterator[NE]] =
      Enumeratee.map[Iterator[E]](_.map(f))
  }

}
