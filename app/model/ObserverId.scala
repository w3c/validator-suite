package org.w3.vs.model

import java.util.UUID

/**
 * Abstraction for an Observer id
 * It's basically a wrapper for a UUID
 */
case class ObserverId(uuid: UUID) {
  
  override def toString: String =
    uuid.toString
  
}

object ObserverId {
  
  def apply(): ObserverId = ObserverId(UUID.randomUUID())
  def apply(uuid: String): ObserverId = ObserverId(UUID.fromString(uuid))
  def fromString(uuid: String): ObserverId = ObserverId(uuid)
  
}