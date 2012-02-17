package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime

case class Job(
  id: Job#Id,
  strategy: Strategy,
  currentRun: Option[DateTime],
  previousRun: Option[DateTime]) {
  
  type Id = UUID
  
}

object Job {
  
  def apply(strategy: Strategy): Job = Job(UUID.randomUUID(), strategy, None, None)
  
}