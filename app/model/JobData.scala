package org.w3.vs.model

import org.joda.time.DateTime
import scala.math._

case class JobData (
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0) {
  
  def health: Int = JobData.health(resources, errors, warnings)

  def sameAs(data: JobData): Boolean = {
    errors == data.errors &&
    warnings == data.warnings &&
    resources == data.resources
  }

}

object JobData {

  def health(resources: Int, errors: Int, warnings: Int): Int = {
    if (resources == 0) -1
    else {
      val errorAverage = errors.toDouble / resources.toDouble
      val h = (exp(log(0.5) / 10 * errorAverage) * 100).toInt
      max(1, h)
    }
  }

}
