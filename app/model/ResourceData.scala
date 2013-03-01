package org.w3.vs.model

import org.w3.util.URL
import org.joda.time.DateTime
import scala.concurrent._
import org.w3.vs.VSConfiguration

case class ResourceData(
  url: URL,
  lastValidated: DateTime,
  warnings: Int,
  errors: Int)
