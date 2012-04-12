package org.w3.vs.model

import java.util.UUID
import scalaz._

case class JobId(private val uuid: UUID = UUID.randomUUID()) {
  
  def shortId: String = toString.substring(0, 6)

  override def toString = uuid.toString
  
}

object JobId {
  
  def fromString(s: String): JobId = JobId(UUID.fromString(s))
  
  def newId(): JobId = JobId(UUID.randomUUID())

  implicit val equalJobId: Equal[JobId] = new Equal[JobId] {
    def equal(left: JobId, right: JobId): Boolean =
      left.uuid == right.uuid
  }
  
}
