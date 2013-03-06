package org.w3.vs.model

import org.joda.time.DateTime

case class ResultStep(run: Run, actions: Seq[RunAction], events: List[RunEvent]) {

  /** computes the JobData-s to fire, resulting from a ResultStep */
  def jobDataToFire(job: Job): Option[JobData] = {
    import job.{ id => jobId, name, strategy }

    // that can be dangerous: we're assuming that the first event is a
    // normal event, and that the second event is always a
    // DoneRunEvent
    val mostInterestingEvent: RunEvent = events match {
      case List(_, e) => e
      case List(e) => e
    }

    def status: JobDataStatus = mostInterestingEvent match {
      case DoneRunEvent(_, _, _, _, _, _, _) => JobDataIdle
      case _ => JobDataRunning(run.progress)
    }

    // the timestamp for an ending event
    // otherwise: defaults to the latest finished job
    def completedOn: Option[DateTime] = mostInterestingEvent match {
      case DoneRunEvent(_, _, _, _, _, _, t) => Some(t)
      case _ => job.latestDone.map(_.completedOn)
    }

    def jobData: JobData =
      JobData(jobId, name, strategy.entrypoint, status, completedOn, run.warnings, run.errors, run.numberOfKnownUrls, strategy.maxResources, run.health)

    // tells if it's worth publishing this event
    def shouldPublish =  mostInterestingEvent match {
      case CreateRunEvent(_, _, _, _, _, _, _) => true
      case DoneRunEvent(_, _, _, _, _, _, _) => true
      case ResourceResponseEvent(_, _, _, _: HttpResponse, _) => true
      case AssertorResponseEvent(_, _, _, ar: AssertorResult, _) => ar.errors != 0 && ar.warnings != 0
      case _ => false
    }

    if (shouldPublish) Some(jobData) else None

  }

}
