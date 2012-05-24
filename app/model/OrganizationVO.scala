package org.w3.vs.model

case class OrganizationVO(
    id: OrganizationId = OrganizationId(),
    name: String,
    admin: UserId)
