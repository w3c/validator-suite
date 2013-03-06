package org.w3.vs.model

import org.w3.util.URL
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json._
import play.api.libs.json.Json._
import org.w3.vs.view.Helper
import org.w3.vs.store.Formats._

case class JobData (
  jobId: JobId,
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
      jobId = job.id,
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

  // Rewrites the json serialization of a jobData to a form suited for the view
  val viewEnumeratee: Enumeratee[JobData, JsValue] = Enumeratee.map {job =>
    val json: JsValue = Json.toJson(job)
    val id = json \ "_id" \ "$oid"
    // TODO: This must be implemented client side. temporary
    val completedOn = if (!(json \ "completedOn").isInstanceOf[JsUndefined]) {
      val timestamp = new DateTime((json \ "completedOn").as[Long])
      Json.obj(
        "timestamp" -> toJson(timestamp.toString()),
        "legend1" -> toJson(Helper.formatTime(timestamp)),
        "legend2" -> toJson("") /* the legend is hidden for now. Doesn't make sense to compute it here anyway */
      )
    } else {
      Json.obj("legend1" -> toJson("Never"))
    }
    // Replace the _id field with id and replace completedOn by its object
    json.asInstanceOf[JsObject] -
      "_id" +
      ("id", id) -
      "completedOn" +
      ("completedOn", completedOn)
  }

}
