package org.w3.vs.model

import java.util.UUID
import scalaz.Equal

case class JobId(private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object JobId {
  
  def fromString(s: String): JobId = JobId(UUID.fromString(s))
  
  implicit val equalJobId: Equal[JobId] = new Equal[JobId] {
    def equal(left: JobId, right: JobId): Boolean =
      left.uuid == right.uuid
  }  
  
}