package org.w3.vs

import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.util.URL

package object observer {

  type Assertions = Iterable[(URL, AssertorId, Either[Throwable, Assertion])]

}