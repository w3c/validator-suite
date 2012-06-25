package org.w3.vs.model

import org.joda.time._

/* any event that has an impact on the state of a run */
trait RunEvent {

  def timestamp: DateTime

}
