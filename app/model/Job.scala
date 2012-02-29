package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime

case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: Strategy,
  createdAt: DateTime = new DateTime,
  creator: String = "john doe") {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)
  
}
