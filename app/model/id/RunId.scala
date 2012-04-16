package org.w3.vs.model

import java.util.UUID
import scalaz.Equal

case class RunId(private val uuid: UUID) {

  def shortId: String = toString.substring(0, 6)

  override def toString = uuid.toString

}

object RunId {

  def fromString(s: String): RunId = RunId(UUID.fromString(s))

  def newId(): RunId = RunId(UUID.randomUUID())

  implicit val equalRunId: Equal[RunId] = new Equal[RunId] {
    def equal(left: RunId, right: RunId): Boolean =
      left.uuid == right.uuid
  }

}
