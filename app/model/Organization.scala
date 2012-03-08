package org.w3.vs.model

import java.util.UUID

object Organization {
  // just to create quickly a fake organization
  def fake = Organization(name = "@@ fake organization @@")
}

case class Organization(
    id: Organization#Id = UUID.randomUUID,
    name: String) {
  
  type Id = UUID
  
}
