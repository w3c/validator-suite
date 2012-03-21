package org.w3.vs.model

import java.util.UUID

case class UserId(private val uuid: UUID)

object UserId {

  def fromString(s: String): UserId = UserId(UUID.fromString(s))

  def newId(): UserId = UserId(UUID.randomUUID())

}
