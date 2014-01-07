package org.w3.vs.model

import org.joda.time.{DateTimeZone, DateTime}
import scalaz.Scalaz._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.w3.vs.util.implicits._
import org.w3.vs.Database
import org.w3.vs.store.Formats._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.FailoverStrategy

case class Coupon(
  id: String,
  campaign: String,
  description: Option[String],
  credits: Int,
  expirationDate: DateTime = DateTime.now(DateTimeZone.UTC).plusYears(1),
  useDate: Option[DateTime] = None,
  usedBy: Option[UserId] = None) {

  def isUsed: Boolean = usedBy.isDefined

  def isExpired = {
    expirationDate <= DateTime.now(DateTimeZone.UTC)
  }

}

object Coupon {

  implicit val writes: Format[Coupon] = (
    (__ \ 'id).format[String] and
    (__ \ 'campaign).format[String] and
    (__ \ 'description).formatNullable[String] and
    (__ \ 'credits).format[Int] and
    (__ \ 'expirationDate).format[DateTime] and
    (__ \ 'useDate).formatNullable[DateTime] and
    (__ \ 'usedBy).formatNullable[UserId]
  )(Coupon.apply, unlift(Coupon.unapply))

  def collection(implicit conf: Database): BSONCollection =
    conf.db("coupons", failoverStrategy = FailoverStrategy(retries = 0))

}
