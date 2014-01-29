package controllers

import org.w3.vs.controllers._
import org.w3.vs.{Metrics, model}
import play.api.i18n.Messages
import org.w3.vs.view.Forms._
import org.w3.vs.exception.{CouponException, UnauthorizedException}
import concurrent.Future

object User extends VSController {

  val logger = play.Logger.of("controllers.User")

  import scala.concurrent.ExecutionContext.Implicits.global

  def profile = AuthenticatedAction("back.account") { implicit req => user =>
    for {
      redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
    } yield {
      Ok(views.html.profile(
        userForm = AccountForm(user),
        passwordForm = PasswordForm,
        couponForm = CouponForm,
        redeemedCoupons = redeemedCoupons,
        user = user))
    }
  }

  def editAction: ActionA = AuthenticatedAction("form.editAccount") { implicit req => user =>
    AccountForm.bindFromRequest().fold(
      form => {
        Metrics.form.editAccountFailure()
        for {
          redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
        } yield {
          render {
            case Accepts.Html() => BadRequest(views.html.profile(form, PasswordForm, CouponForm, redeemedCoupons, user))
            case Accepts.Json() => BadRequest
          }
        }
      },
      account => {
        for {
          saved <- model.User.update(account.update(user))
        } yield {
          logger.info(s"""id=${user.id} action=editprofile message="profile updated" """)
          render {
            case Accepts.Html() => SeeOther(routes.User.profile().url).withSession(("email" -> saved.email)).flashing(("success" -> Messages("user.profile.updated")))
            case Accepts.Json() => Ok
          }
        }
      }
    )
  }

  def changePasswordAction = AuthenticatedAction("form.editPassword") { implicit req => user =>
    PasswordForm.bindFromRequest().fold (
      formWithErrors => {
        Metrics.form.editPasswordFailure()
        for {
          redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
        } yield BadRequest(views.html.profile(AccountForm(user), formWithErrors, CouponForm, redeemedCoupons, user))
      },
      password => {
        (for {
          _ <- model.User.authenticate(user.email, password.current)
          saved <- model.User.update(user.withPassword(password.newPassword))
        } yield {
          logger.info(s"""id=${user.id} action=editpassword message="password updated" """)
          SeeOther(routes.User.profile().url).flashing(("success" -> Messages("user.password.updated")))
        }).recoverWith {
          case UnauthorizedException(email) =>
            for {
              redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
            } yield BadRequest(views.html.profile(AccountForm(user), PasswordForm, CouponForm, redeemedCoupons, user, List("error" -> Messages("application.invalidPassword"))))
        }
      }
    )
  }

  def redeemCouponAction = AuthenticatedAction("form.redeemCoupon") { implicit req => user =>
    CouponForm.bindFromRequest().fold (
      formWithErrors => {
        for {
          redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
        } yield BadRequest(views.html.profile(AccountForm(user), PasswordForm, formWithErrors, redeemedCoupons, user))
      },
      coupon => {
        (for {
          (user, coupon) <- model.Coupon.redeem(coupon, user.id)
        } yield {
          logger.info(s"""id=${user.id} action=couponRedeemed message="coupon redeemed: ${coupon}" """)
          SeeOther(routes.User.profile().url).flashing(("success" -> Messages("user.coupon.redeemed", coupon.description.getOrElse("Validator Suite"), coupon.code, coupon.credits)))
        }) recoverWith {
          case CouponException(code, msg) =>
            val form = CouponForm.withError("coupon", msg)
            for {
              redeemedCoupons <- model.Coupon.getRedeemedBy(user.id)
            } yield BadRequest(views.html.profile(AccountForm(user), PasswordForm, form, redeemedCoupons, user))
        }
      }
    )
  }

}
