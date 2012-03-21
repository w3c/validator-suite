package org.w3.vs.model

import java.util.UUID
import scalaz._

case class OrganizationId(private val uuid: UUID) {
  
  override def toString = uuid.toString
  
}

object OrganizationId {
  
  def fromString(s: String): OrganizationId = OrganizationId(UUID.fromString(s))
  
  def newId(): OrganizationId = OrganizationId(UUID.randomUUID())

  implicit val equalOrganizationId: Equal[OrganizationId] = new Equal[OrganizationId] {
    def equal(left: OrganizationId, right: OrganizationId): Boolean =
      left.uuid == right.uuid
  }
  
}
