package org.w3.vs.actor.message

import org.joda.time._

// Could be Run/Cancel to be consistent with the ui?
case class Refresh(timestamp: DateTime = DateTime.now(DateTimeZone.UTC))
case class Stop(timestamp: DateTime = DateTime.now(DateTimeZone.UTC))

case class BeProactive(timestamp: DateTime = DateTime.now(DateTimeZone.UTC))
case class BeLazy(timestamp: DateTime = DateTime.now(DateTimeZone.UTC))

case object GetRun
case object NoMorePendingAssertion

case object GetOrgEnumerator
case object GetJobEnumerator
