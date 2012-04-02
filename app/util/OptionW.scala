package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._
import org.w3.util.Pimps._

class OptionW[S](opt: Option[S]) {

  def toImmediateSuccess[F](ifNone: F)(implicit context: ExecutionContext): FutureValidationNoTimeOut[F, S] =
    FutureValidation(Promise.successful(opt.toSuccess(ifNone)))

}
