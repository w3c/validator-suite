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
case class StrategyId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class OrganizationId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)
case class SnapshotId (private val uuid: UUID = UUID.randomUUID()) extends Id(uuid)

object JobId {
  def fromString(s: String): JobId = JobId(UUID.fromString(s))
  implicit val equalJobId: Equal[JobId] = new Equal[JobId] {
    def equal(left: JobId, right: JobId): Boolean =
      left.uuid == right.uuid
  }  
}

object RunId {
  def fromString(s: String): RunId = RunId(UUID.fromString(s))
  implicit val equalRunId: Equal[RunId] = new Equal[RunId] {
    def equal(left: RunId, right: RunId): Boolean =
      left.uuid == right.uuid
  }
}

object UserId {
  def fromString(s: String): UserId = UserId(UUID.fromString(s))
  implicit val equalUserId: Equal[UserId] = new Equal[UserId] {
    def equal(left: UserId, right: UserId): Boolean =
      left.uuid == right.uuid
  }
}

object StrategyId {
  def fromString(s: String): StrategyId = StrategyId(UUID.fromString(s))
  implicit val equalStrategyId: Equal[StrategyId] = new Equal[StrategyId] {
    def equal(left: StrategyId, right: StrategyId): Boolean =
      left.uuid == right.uuid
  }
}

object OrganizationId {
  def fromString(s: String): OrganizationId = OrganizationId(UUID.fromString(s))
  implicit val equalOrganizationId: Equal[OrganizationId] = new Equal[OrganizationId] {
    def equal(left: OrganizationId, right: OrganizationId): Boolean =
      left.uuid == right.uuid
  }
}

object SnapshotId {
  def fromString(s: String): SnapshotId = SnapshotId(UUID.fromString(s))
  implicit val equalSnapshotId: Equal[SnapshotId] = new Equal[SnapshotId] {
    def equal(left: SnapshotId, right: SnapshotId): Boolean =
      left.uuid == right.uuid
  }
}

