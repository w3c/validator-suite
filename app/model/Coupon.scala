package org.w3.vs.model

import org.joda.time.{DateTime, DateTimeZone}
import akka.pattern.{ask, AskTimeoutException}
import org.w3.vs.util.implicits._
import org.w3.vs.store.MongoStore.journalCommit
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import scala.concurrent.{ops => _, _}
import scala.concurrent.ExecutionContext.Implicits.global
import exception._
import scalaz.Scalaz._
import play.modules.reactivemongo.json.ImplicitBSONHandlers._

import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.collections.default.BSONCollection

case class Coupon(
  id: CouponId = CouponId(),
  code: String,
  campaign: String,
  description: Option[String],
  credits: Int,
  expirationDate: DateTime,
  useDate: Option[DateTime] = None,
  usedBy: Option[UserId] = None) {

  def isUsed: Boolean = usedBy.isDefined

  def isExpired = {
    expirationDate <= DateTime.now(DateTimeZone.UTC)
  }

  def save()(implicit conf: Database): Future[Coupon] = {
    Coupon.save(this).map(_ => this)
  }

  def update()(implicit conf: Database): Future[Coupon] = {
    Coupon.update(this).map(_ => this)
  }

  def compactString = {
    s"""${id} - Code: ${code} - Campaign: ${campaign} - Description: "${description.getOrElse("none")}" - Credits: ${credits} - Expires: ${expirationDate} - UseDate: ${useDate.getOrElse("Never")} - UsedBy: ${usedBy.getOrElse("NoOne")}"""
  }

}

object Coupon {

  // e.g. PREFIX-ABCD-EFGH-0123-4567
  val pattern = """^\w{2,8}(-[0-9A-Z]{4}){3}$""".r

  def checkSyntax(code: String) {
    if (!pattern.findFirstIn(code).isDefined) throw new InvalidSyntaxCouponException(code)
  }

  def generateCode(prefix: String): String = {
    def random = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(4).toUpperCase()
    s"${prefix}-${random}-${random}-${random}"
  }

  def delete(code: String)(implicit conf: ValidatorSuite): Future[Unit] = {
    val query = Json.obj("code" -> toJson(code))
    collection.remove[JsValue](query) map {
      lastError => ()
    }
  }

  def delete(id: CouponId)(implicit conf: ValidatorSuite): Future[Unit] = {
    val query = Json.obj("_id" -> toJson(id))
    collection.remove[JsValue](query) map {
      lastError => ()
    }
  }

  def apply(
    code: String,
    campaign: String,
    credits: Int)(implicit conf: ValidatorSuite): Coupon = {
    val confValidityDays = conf.config.getInt("application.expireDate.couponValidityInDays").getOrElse(90)
    Coupon(
      code = code,
      campaign = campaign,
      description = None,
      credits = credits,
      expirationDate = DateTime.now(DateTimeZone.UTC).plusDays(confValidityDays)
    )
  }

  def apply(
    code: String,
    campaign: String,
    credits: Int,
    description: String,
    validityDays: Int)(implicit conf: ValidatorSuite): Coupon = {
    Coupon(
      code = code,
      campaign = campaign,
      description = Some(description),
      credits = credits,
      expirationDate = DateTime.now(DateTimeZone.UTC).plusDays(validityDays)
    )
  }

  def getIfValid(couponCode: String)(implicit vs: ValidatorSuite with Database): Future[Coupon] = {
      get(couponCode).map{ coupon =>
        if (coupon.usedBy.isDefined) { throw new AlreadyUsedCouponException(couponCode) }
        if (coupon.expirationDate.isBefore(DateTime.now(DateTimeZone.UTC))) { throw new ExpiredCouponException(couponCode) }
        coupon
      }
  }

  def getIfValid(couponId: CouponId)(implicit vs: ValidatorSuite with Database): Future[Coupon] = {
    get(couponId).map{ coupon =>
      if (coupon.usedBy.isDefined) { throw new AlreadyUsedCouponException(coupon.code) }
      if (coupon.expirationDate.isBefore(DateTime.now(DateTimeZone.UTC))) { throw new ExpiredCouponException(coupon.code) }
      coupon
    }
  }

  def redeem(couponCode: String, userId: UserId)(implicit vs: ValidatorSuite with Database): Future[(User, Coupon)] = {
    for {
      coupon <- getIfValid(couponCode)
      user <- model.User.updateCredits(userId, coupon.credits)
      redeemed <-
        coupon.copy(useDate = Some(DateTime.now(DateTimeZone.UTC)), usedBy = Some(userId)).update()
    } yield (user, redeemed)
  }

  def redeem(couponId: CouponId, userId: UserId)(implicit vs: ValidatorSuite with Database): Future[(User, Coupon)] = {
    for {
      coupon <- getIfValid(couponId)
      user <- model.User.updateCredits(userId, coupon.credits)
      redeemed <-
      coupon.copy(useDate = Some(DateTime.now(DateTimeZone.UTC)), usedBy = Some(userId)).update()
    } yield (user, redeemed)
  }

  def collection(implicit conf: Database): BSONCollection =
    conf.db("coupons", failoverStrategy = FailoverStrategy(retries = 0))

  def getAll()(implicit conf: Database): Future[List[Coupon]] = {
    val cursor = collection.find(Json.obj()).cursor[JsValue]
    cursor.collect[List]() map {
      list => list flatMap { coupon =>
        try {
          Some(coupon.as[Coupon])
        } catch { case t: Throwable =>
          None
        }
      }
    }
  }

  def getCampaign(campaign: String)(implicit conf: Database): Future[List[Coupon]] = {
    val cursor = collection.find(Json.obj("campaign" -> campaign)).cursor[JsValue]
    cursor.collect[List]() map {
      list => list flatMap { coupon =>
        try {
          Some(coupon.as[Coupon])
        } catch { case t: Throwable =>
          None
        }
      }
    }
  }

  def get(id: CouponId)(implicit conf: Database): Future[Coupon] = {
    val query = Json.obj("_id" -> toJson(id))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption map {
      case None => throw new NoSuchCouponException(id.toString)
      case Some(json) => json.as[Coupon]
    }
  }

  def get(code: String)(implicit conf: Database): Future[Coupon] = {
    val query = Json.obj("code" -> toJson(code))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption map {
      case None => throw new NoSuchCouponException(code)
      case Some(json) => json.as[Coupon]
    }
  }

  def getRedeemedBy(userId: UserId)(implicit conf: ValidatorSuite with Database): Future[List[Coupon]] = {
    val cursor = collection.find(Json.obj("usedBy" -> userId)).cursor[JsValue]
    cursor.collect[List]() map {
      list => list flatMap { coupon =>
        try {
          Some(coupon.as[Coupon])
        } catch { case t: Throwable =>
          None
        }
      }
    }
  }

  def save(coupon: Coupon)(implicit conf: Database): Future[Unit] = {
    val couponJson = toJson(coupon)
    import reactivemongo.core.commands.LastError
    collection.insert(couponJson, writeConcern = journalCommit) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _, _, _) => throw new DuplicateCouponException(coupon.code)
    }
  }

  def update(coupon: Coupon)(implicit conf: Database): Future[Unit] = {
    val selector = Json.obj("_id" -> toJson(coupon.id))
    val update = toJson(coupon)
    collection.update(selector, update, writeConcern = journalCommit) map {
      lastError => if (!lastError.ok) throw lastError
    }
  }

}
