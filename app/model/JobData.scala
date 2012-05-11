package org.w3.vs.model

import org.w3.vs.actor._

object JobData {

  def apply(data: RunData): JobData = JobData(
    jobId = data.jobId,
    runId = data.runId,
    activity = data.activity,
    explorationMode = data.explorationMode,
    resources = data.numberOfKnownUrls,
    oks = data.oks,
    errors = data.errors,
    warnings = data.warnings)

  def health(jobDataOpt: Option[JobData]): Int = jobDataOpt match {
    case Some(data) => {
      val errorAverage = data.errors.toDouble / data.resources.toDouble
      (scala.math.exp(scala.math.log(0.5) / 10 * errorAverage) * 100).toInt
    }
    case _ => 0
  }

}

case class JobData(
    jobId: JobId,
    runId: RunId,
    activity: RunActivity,
    explorationMode: ExplorationMode,
    resources: Int,
    oks: Int,
    errors: Int,
    warnings: Int
) {
  def health: Int = JobData.health(Some(this))
}
