package org.w3.vs.model

import org.joda.time.DateTime
import scala.math._
import scalaz.Equal

case class RunData (
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    status: JobDataStatus = JobDataIdle,
    completedOn: Option[DateTime] = None) {
  
  def health: Int = RunData.health(resources, errors, warnings)

}

object RunData {

  implicit val equal = Equal.equalA[RunData]

  def health(resources: Int, errors: Int, warnings: Int): Int = {
    if (resources == 0) -1
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      val h = (exp(log(0.5) / 10 * errorAverage) * 100).toInt
      max(1, h)
    }
  }

}
