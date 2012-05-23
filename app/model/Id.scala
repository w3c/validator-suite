package org.w3.vs.model

import scalaz.Equal
import java.util.UUID

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
  implicit val equal: Equal[JobId] = new Equal[JobId] {
    def equal(left: JobId, right: JobId): Boolean =
      left.uuid == right.uuid
  }  
}

object RunId {
  def apply(s: String): RunId = RunId(UUID.fromString(s))
  implicit val equal: Equal[RunId] = new Equal[RunId] {
    def equal(left: RunId, right: RunId): Boolean =
      left.uuid == right.uuid
  }
}

object UserId {
  def apply(s: String): UserId = UserId(UUID.fromString(s))
  implicit val equal: Equal[UserId] = new Equal[UserId] {
    def equal(left: UserId, right: UserId): Boolean =
      left.uuid == right.uuid
  }
}

object JobDataId {
  def apply(s: String): JobDataId = JobDataId(UUID.fromString(s))
  implicit val equal: Equal[JobDataId] = new Equal[JobDataId] {
    def equal(left: JobDataId, right: JobDataId): Boolean =
      left.uuid == right.uuid
  }
}

object ContextId {
  def apply(s: String): ContextId = ContextId(UUID.fromString(s))
  implicit val equal: Equal[ContextId] = new Equal[ContextId] {
    def equal(left: ContextId, right: ContextId): Boolean =
      left.uuid == right.uuid
  }
}

object AssertorId {
  def apply(s: String): AssertorId = AssertorId(UUID.fromString(s))
  implicit val equal: Equal[AssertorId] = new Equal[AssertorId] {
    def equal(left: AssertorId, right: AssertorId): Boolean =
      left.uuid == right.uuid
  }
}

object StrategyId {
  def apply(s: String): StrategyId = StrategyId(UUID.fromString(s))
  implicit val equal: Equal[StrategyId] = new Equal[StrategyId] {
    def equal(left: StrategyId, right: StrategyId): Boolean =
      left.uuid == right.uuid
  }
}

object AssertionId {
  def apply(s: String): AssertionId = AssertionId(UUID.fromString(s))
  implicit val equal: Equal[AssertionId] = new Equal[AssertionId] {
    def equal(left: AssertionId, right: AssertionId): Boolean =
      left.uuid == right.uuid
  }
}

object OrganizationId {
  def apply(s: String): OrganizationId = OrganizationId(UUID.fromString(s))
  implicit val equal: Equal[OrganizationId] = new Equal[OrganizationId] {
    def equal(left: OrganizationId, right: OrganizationId): Boolean =
      left.uuid == right.uuid
  }
}

object ResourceResponseId {
  def apply(s: String): ResourceResponseId = ResourceResponseId(UUID.fromString(s))
  implicit val equal: Equal[ResourceResponseId] = new Equal[ResourceResponseId] {
    def equal(left: ResourceResponseId, right: ResourceResponseId): Boolean =
      left.uuid == right.uuid
  }
}

object AssertorResponseId {
  def apply(s: String): AssertorResponseId = AssertorResponseId(UUID.fromString(s))
  implicit val equal: Equal[AssertorResponseId] = new Equal[AssertorResponseId] {
    def equal(left: AssertorResponseId, right: AssertorResponseId): Boolean =
      left.uuid == right.uuid
  }
}

