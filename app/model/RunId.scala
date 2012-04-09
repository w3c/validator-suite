package org.w3.vs.model

import java.util.UUID

case class RunId(private val uuid: UUID)

object RunId {

  def fromString(s: String): RunId = RunId(UUID.fromString(s))

  def newId(): RunId = RunId(UUID.randomUUID())

}
