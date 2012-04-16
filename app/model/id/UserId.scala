package org.w3.vs.model

import java.util.UUID

case class UserId(private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object UserId {

  def fromString(s: String): UserId = UserId(UUID.fromString(s))

}
