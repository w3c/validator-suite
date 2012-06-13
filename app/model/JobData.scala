package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.actor._
import org.joda.time.DateTime
import scala.math._

case class JobData (
    id: JobDataId,
    runId: RunId,
    resources: Int,
    errors: Int,
    warnings: Int,
    timestamp: DateTime) {
  
  def health: Int = JobData.health(resources, errors, warnings)

}

object JobData {

  def health(resources: Int, errors: Int, warnings: Int): Int = {
    if (resources == 0) 0
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      val h = (exp(log(0.5) / 10 * errorAverage) * 100).toInt
      scala.math.max(1, h)
    }
  }

}
