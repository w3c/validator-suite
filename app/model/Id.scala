package org.w3.vs.model

import scalaz.Equal
import java.util.UUID

object Id {
  
  implicit def toId(e: Job): JobId = e.id
  implicit def toId(e: Run): RunId = e.id
  implicit def toId(e: User): UserId = e.id
  implicit def toId(e: JobData): JobDataId = e.id
  implicit def toId(e: Context): ContextId = e.id
  //implicit def toId(e: Assertor): AssertorId = e.id
  implicit def toId(e: Strategy): StrategyId = e.id
  implicit def toId(e: Assertion): AssertionId = e.id
  implicit def toId(e: Organization): OrganizationId = e.id
  implicit def toId(e: ResourceResponse): ResourceResponseId = e.id
  implicit def toId(e: AssertorResponse): AssertorResponseId = e.id
  
}

class Id(private val uuid: UUID = UUID.randomUUID()) {
  def shortId: String = toString.substring(0, 6)
  override def toString = uuid.toString
}

case class JobId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class RunId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class UserId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class JobDataId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class ContextId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class AssertorId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class StrategyId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class AssertionId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class OrganizationId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class ResourceResponseId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class AssertorResponseId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object JobId {
  def apply(s: String): JobId = JobId(UUID.fromString(s))
  implicit val equal = Equal.equalA[JobId]
}

object RunId {
  def apply(s: String): RunId = RunId(UUID.fromString(s))
  implicit val equal = Equal.equalA[RunId]
}

object UserId {
  def apply(s: String): UserId = UserId(UUID.fromString(s))
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

object AssertorId {
  def apply(s: String): AssertorId = AssertorId(UUID.fromString(s))
  implicit val equal = Equal.equalA[AssertorId]
}

object StrategyId {
  def apply(s: String): StrategyId = StrategyId(UUID.fromString(s))
  implicit val equal = Equal.equalA[StrategyId]
}

object AssertionId {
  def apply(s: String): AssertionId = AssertionId(UUID.fromString(s))
  implicit val equal = Equal.equalA[AssertionId]
}

object OrganizationId {
  def apply(s: String): OrganizationId = OrganizationId(UUID.fromString(s))
  implicit val equal = Equal.equalA[OrganizationId]
}

object ResourceResponseId {
  def apply(s: String): ResourceResponseId = ResourceResponseId(UUID.fromString(s))
  implicit val equal = Equal.equalA[ResourceResponseId]
}

object AssertorResponseId {
  def apply(s: String): AssertorResponseId = AssertorResponseId(UUID.fromString(s))
  implicit val equal = Equal.equalA[AssertorResponseId]
}

