package org.w3.vs.model

import java.util.UUID

case class OrganizationId(private val uuid: UUID) {
  
  override def toString = uuid.toString
  
}

object OrganizationId {
  
  def fromString(s: String): OrganizationId = OrganizationId(UUID.fromString(s))
  
  def newId(): OrganizationId = OrganizationId(UUID.randomUUID())
  
}
