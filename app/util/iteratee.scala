package org.w3.vs.util

import play.api.libs.iteratee._
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext

/** utility functions for Play's Iteratee-s and Enumerator-s */
package object iteratee {

  val logger = play.Logger.of("org.w3.vs.util.iteratee")

  def waitFor[A] = new {

    def apply[E]()(implicit classTag: ClassTag[A]): Iteratee[E, A] = Cont {
      case Input.El(classTag(a)) => Done(a)
      case in @ Input.EOF => Error("couln't find an element that matches the partial function", in)
      case _ => apply()(classTag)
    }

    def apply[B](pf: PartialFunction[A, B]): Iteratee[A, B] = Cont {
      case Input.El(a) if pf.isDefinedAt(a) => Done(pf(a))
      case in @ Input.EOF => Error("couln't find an element that matches the partial function", in)
      case _ => apply(pf)
    }
  }

  /** Enumeratee that maps Input.Empty to Input.EOF */
  def endWithEmpty[T]()(implicit ec: ExecutionContext): Enumeratee[T, T] =
    Enumeratee.mapInput[T] {
      case Input.Empty => Input.EOF
      case t => t
    }

  /** Enumeratee that just passes the elements and prints them -- for test only */
  def eprint[T]()(implicit ec: ExecutionContext): Enumeratee[T, T] = Enumeratee.map { t => println("** "+t); t }

}
