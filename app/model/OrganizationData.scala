package org.w3.vs.model

import java.util.UUID

object OrganizationData {
  // just to create quickly a fake organization
  def fake = OrganizationData(name = "@@ fake organization @@")
}

case class OrganizationData(
    id: OrganizationId = OrganizationId(),
    name: String)
