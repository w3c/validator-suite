package org.w3.vs.model

import org.w3.vs.web.URL
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.json.Json._
import org.w3.vs.view.Helper
import org.w3.vs.store.Formats._

case class JobData (
  id: JobId,
  name: String,
  entrypoint: URL,
  status: JobDataStatus,
  completedOn: Option[DateTime],
  warnings: Int,
  errors: Int,
  resources: Int,
  maxResources: Int,
  health: Int
)

object JobData {

  def apply(job: Job, run: Run): JobData = {
    val runData = run.data
    JobData(
      id = job.id,
      name = job.name,
      entrypoint = job.strategy.entrypoint,
      status = run.jobDataStatus,
      completedOn = run.completedOn,
      warnings = runData.warnings,
      errors = runData.errors,
      resources = runData.resources,
      maxResources = job.strategy.maxResources,
      health = runData.health
    )
  }

  def apply(job: Job, runData: RunData): JobData = {
    new JobData(
      id = job.id,
      name = job.name,
      entrypoint = job.strategy.entrypoint,
      status = runData.status,
      completedOn = if (runData.completedOn.isDefined) runData.completedOn else job.latestDone.map(_.completedOn),
      warnings = runData.warnings,
      errors = runData.errors,
      resources = runData.resources,
      maxResources = job.strategy.maxResources,
      health = runData.health
    )
  }

}
