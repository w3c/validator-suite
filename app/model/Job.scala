package org.w3.vs.model

import java.util.UUID
import org.joda.time.DateTime

case class Job(
  id: Job#Id = UUID.randomUUID,
  strategy: EntryPointStrategy,
  createdAt: DateTime = new DateTime,
  creator: String = "john doe",
  name: String = "myJob") {
  
  type Id = UUID
  
  def shortId: String = id.toString.substring(0, 6)
  
  def withNewId(id: Id) = this.copy(id = id)
  
}
