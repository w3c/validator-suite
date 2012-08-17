package org.w3.vs.actor.message

/* user generated events */

case object Refresh
case object Stop
case object BeProactive
case object BeLazy

/* events internal to the application */

case object GetRun
case object NoMorePendingAssertion
case object GetOrgEnumerator
case object GetJobEnumerator
