package org.w3.vs.model

import org.w3.vs._
import scalaz.Equal
import reactivemongo.bson._

class Id(val oid: BSONObjectID = BSONObjectID.generate) {
  def shortId: String = toString.substring(15, toString.length)
  def id: String = oid.stringify
  override def toString = oid.stringify
}

case class CouponId (override val oid: BSONObjectID = BSONObjectID.generate) extends Id(oid)
case class JobId (override val oid: BSONObjectID = BSONObjectID.generate) extends Id(oid)
case class RunId (override val oid: BSONObjectID = BSONObjectID.generate) extends Id(oid)
case class UserId (override val oid: BSONObjectID = BSONObjectID.generate) extends Id(oid)
case class AssertorId (id: String) { override def toString = id }

object CouponId {
  def apply(s: String): CouponId = CouponId(BSONObjectID(s))
  implicit val equal = Equal.equalA[CouponId]
}

object JobId {
  def apply(s: String): JobId = JobId(BSONObjectID(s))
  implicit val equal = Equal.equalA[JobId]
}

object RunId {
  def apply(s: String): RunId = RunId(BSONObjectID(s))
  implicit val equal = Equal.equalA[RunId]
}

object UserId {
  def apply(s: String): UserId = UserId(BSONObjectID(s))
  implicit val equal = Equal.equalA[UserId]
}

object AssertorId {
  implicit val equal = Equal.equalA[AssertorId]
}


case class PasswordResetId (override val oid: BSONObjectID = BSONObjectID.generate) extends Id(oid)

object PasswordResetId {
  def apply(s: String): PasswordResetId = PasswordResetId(BSONObjectID(s))
  implicit val equal = Equal.equalA[PasswordResetId]
}