package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.banana._
import scalaz.Scalaz._
import scalaz._

case class Context(
    content: String,
    line: Option[Int], 
    column: Option[Int])
