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

  /** computes the JobData-s to fire, resulting from a ResultStep */
  def toFire(job: Job, resultStep: ResultStep): Seq[JobData] = {
    import job.{ id => jobId, name, strategy }
    import resultStep.{ run, events }
    // there is at most one JobData per event
    events flatMap { event =>
      def status: JobDataStatus = event match {
        case DoneRunEvent(_, _, _, _, _, _, _) => JobDataIdle
        case _ => JobDataRunning(resultStep.run.progress)
      }
      // the timestamp for an ending event
      // otherwise: defaults to the latest finished job
      def completedOn: Option[DateTime] = event match {
        case DoneRunEvent(_, _, _, _, _, _, t) => Some(t)
        case _ => job.latestDone.map(_.completedOn)
      }
      def jobData: JobData =
        JobData(jobId, name, strategy.entrypoint, status, completedOn, run.warnings, run.errors, run.numberOfKnownUrls, strategy.maxResources, run.health)
      // tells if it's worth publishing this event
      def shouldPublish =  event match {
        case CreateRunEvent(_, _, _, _, _, _, _) => true
        case DoneRunEvent(_, _, _, _, _, _, _) => true
        case ResourceResponseEvent(_, _, _, _: HttpResponse, _) => true
        case AssertorResponseEvent(_, _, _, ar: AssertorResult, _) => ar.errors != 0 && ar.warnings != 0
        case _ => false
      }
      if (shouldPublish) Some(jobData) else None
    }
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
