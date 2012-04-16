package org.w3.vs.model

import java.util.UUID
import scalaz._

case class OrganizationId(private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object OrganizationId {
  
  def fromString(s: String): OrganizationId = OrganizationId(UUID.fromString(s))

  implicit val equalOrganizationId: Equal[OrganizationId] = new Equal[OrganizationId] {
    def equal(left: OrganizationId, right: OrganizationId): Boolean =
      left.uuid == right.uuid
  }
  
}
