package org.w3.vs.model

import org.joda.time._

/* any event that has an impact on the state of a run */
sealed trait RunEvent {
  def runId: RunId
  def timestamp: DateTime
}

case class CreateRunEvent(runId: RunId, strategy: Strategy, createdAt: DateTime, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

object CreateRunEvent {

  def apply(run: Run): CreateRunEvent = {
    import run._
    CreateRunEvent(runId, strategy, createdAt)
  }

}

case class CompleteRunEvent(runId: RunId, resourceDatas: Iterable[ResourceData], timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class CancelRunEvent(runId: RunId, resourceDatas: Iterable[ResourceData], timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class AssertorResponseEvent(runId: RunId, ar: AssertorResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class ResourceResponseEvent(runId: RunId, rr: ResourceResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
