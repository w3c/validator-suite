package org.w3.vs.model

import org.joda.time._

/* any event that has an impact on the state of a run */
sealed trait RunEvent {

  def timestamp: DateTime

}

case class AssertorResponseEvent(ar: AssertorResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
case class ResourceResponseEvent(rr: ResourceResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
case class BeProactiveEvent(timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
case class BeLazyEvent(timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
