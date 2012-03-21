package org.w3.vs.model

import java.util.UUID

case class JobId(private val uuid: UUID = UUID.randomUUID()) {
  
  override def toString = uuid.toString
  
}

object JobId {
  
  def fromString(s: String): JobId = JobId(UUID.fromString(s))
  
  def newId(): JobId = JobId(UUID.randomUUID())
  
}
