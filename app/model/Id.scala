package org.w3.vs.model

import org.w3.vs._
import scalaz.Equal
import java.util.UUID
import org.w3.vs.store.Binders._
import org.w3.vs.diesel._
import reactivemongo.bson._

class Id(val uuid: UUID) {
  def shortId: String = toString.substring(0, 6)
  def id: String = uuid.toString()
  override def toString = uuid.toString()
}

case class JobId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class RunId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class UserId (oid: BSONObjectID = BSONObjectID.generate) /*extends Id(uuid)*/ {
  def shortId: String = toString.substring(0, 6)
  def id: String = oid.stringify
  override def toString = oid.stringify
  def toUri: Rdf#URI = UserUri(this)
}
//case class UserId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid) {
//  def toUri: Rdf#URI = UserUri(this)
//}
case class JobDataId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class ContextId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class StrategyId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class AssertionId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class ResourceResponseId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class AssertorResponseId (override val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object AssertorId {

  val regex = "^[a-zA-Z][_a-zA-Z0-9]*$".r

}

case class AssertorId(id: String) {

  assert(AssertorId.regex.findFirstMatchIn(id).isDefined)

  override def toString = id

}

object JobId {
  def apply(s: String): JobId = JobId(UUID.fromString(s))
  implicit val equal = Equal.equalA[JobId]
}

object RunId {
  def apply(s: String): RunId = RunId(UUID.fromString(s))
  implicit val equal = Equal.equalA[RunId]
}

object UserId {
  def apply(s: String): UserId = UserId(BSONObjectID(s))
  implicit val equal = Equal.equalA[UserId]
}

object JobDataId {
  def apply(s: String): JobDataId = JobDataId(UUID.fromString(s))
  implicit val equal = Equal.equalA[JobDataId]
}

object ContextId {
  def apply(s: String): ContextId = ContextId(UUID.fromString(s))
  implicit val equal = Equal.equalA[ContextId]
}

object StrategyId {
  def apply(s: String): StrategyId = StrategyId(UUID.fromString(s))
  implicit val equal = Equal.equalA[StrategyId]
}

object AssertionId {
  def apply(s: String): AssertionId = AssertionId(UUID.fromString(s))
  implicit val equal = Equal.equalA[AssertionId]
}

object ResourceResponseId {
  def apply(s: String): ResourceResponseId = ResourceResponseId(UUID.fromString(s))
  implicit val equal = Equal.equalA[ResourceResponseId]
}

object AssertorResponseId {
  def apply(s: String): AssertorResponseId = AssertorResponseId(UUID.fromString(s))
  implicit val equal = Equal.equalA[AssertorResponseId]
}

