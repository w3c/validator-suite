package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.{Metrics, model}
import org.w3.vs.model._
import org.w3.vs.util.timer._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.vs.view.Forms._
import play.api.{Mode, Play}

object Purchase extends VSController {

  val logger = play.Logger.of("controllers.Purchase")

  /*def buyJob: ActionA = UserAwareAction { implicit req => user =>
    val messages = user match {
      case Some(user) => List(("info" -> Messages("warning.createOrBuy")))
      case None => List.empty
    }
    Ok(views.html.newOneTimeJob(OneTimeJobForm.blank, user, List.empty))
    /*getUser map {
      case user => Ok(views.html.newOneTimeJob(OneTimeJobForm.blank, user))
    } recover {
      case _: UnauthorizedException =>
        Unauthorized(views.html.register(RegisterForm.redirectTo(req.uri), messages = List(("info", Messages("info.register.first")))))
    } */
  }*/

  def buyCredits: ActionA = AsyncAction("front.buy") { implicit req =>
    (for {
      user <- getUser()
      redirect <- Future.successful{
        for {
          planStr <- req.getQueryString("plan")
          plan <- CreditPlan.fromString(planStr)
        } yield {
          render {
            case Accepts.Html() => {
              if (user.isRoot) {
                model.User.updateCredits(user.id, plan.credits).getOrFail()
                logger.info(s"""id=${user.id} action=credits-update amount=${plan.credits} expiration-date=${user.expireDate.toString("yyyy-MM-dd")} message="${plan.fastSpringKey} purchase by admin" """)
                Redirect(routes.Jobs.index())
              } else {
                logger.info(s"""id=${user.id} action=purchase plan=${plan.fastSpringKey} message="Redirecting to FastSpring" """)
                Metrics.purchases.redirect()
                redirectToStore(plan, user.id)
              }
            }
          }
        }
      }
    } yield {
      redirect.getOrElse {
        render {
          case Accepts.Html() => Redirect(routes.Application.pricing())
        }
      }
    }) recover {
      case UnauthorizedException(_) => {
        render {
          case Accepts.Json() => Unauthorized
          case Accepts.Html() => Unauthorized(views.html.register(
            registerForm = RegisterForm.fill(Register(redirectUri = req.uri)),
            loginForm = LoginForm.fill(Login(redirectUri = req.uri)),
            messages = List(("info", Messages("info.register.first", routes.Application.login().url)))
          ))
        }
      }
    }
  }

  /*def buyJobAction: ActionA = UserAwareAction { implicit req => user =>
    (for {
      form <- Future(OneTimeJobForm.bind match {
        case Left(form) => throw InvalidFormException(form, user)
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      render {
        case Accepts.Html() => {
          user match {
            case Some(user) if user.isRoot => {
              job.run().getOrFail()
              Redirect(routes.Jobs.index)
            }
            case _ => {
              logger.info(s"Redirected user ${user.map(_.email)} to store for job ${routes.Job.get(job.id).toString}")
              redirectToStore(form.plan, job.id)
            }
          }
        }
        case Accepts.Json() => ???
      }
    }) recover {
      case InvalidFormException(form: OneTimeJobForm, _) => {
        render {
          case Accepts.Html() => BadRequest(views.html.newOneTimeJob(form, user))
          case Accepts.Json() => BadRequest
        }
      }
    }
  }*/

  def redirectToStore(product: CreditPlan, id: UserId)(implicit req: RequestHeader) = {
    Play.current.mode match {
      case Mode.Dev => throw new AccessNotAllowed("Purchases have been disabled on the development instance")
      case _ => Redirect(getStoreUrl(product, id))
    }
  }

  /*def redirectToStore(product: OneTimePlan, id: JobId) =
    Redirect(getStoreUrl(product, id))*/

  def getStoreUrl(product: Plan, id: Id) = {
    "https://sites.fastspring.com/ercim/instant/" + product.fastSpringKey + "?referrer=" + id
  }

  def callback = AsyncAction("form.fsCallback") { req =>
    Metrics.purchases.callback()
    logger.debug(req.body.asFormUrlEncoded.toString)
    // TODO: add security check
    (for {
      params <- req.body.asFormUrlEncoded
      orderIdL <- params.get("OrderID")
      orderId <- orderIdL.headOption
      planStringL <- params.get("OrderProductNames").headOption
      planString <- planStringL.headOption
      plan <- Plan.fromFsString(planString)
      idStringL <- params.get("OrderReferrer")
      idString <- idStringL.headOption
    } yield {

      plan match {
       //case plan: OneTimePlan =>
          /*for {
            jobId <- Future(JobId(idString))
            job <- model.Job.get(jobId)
            _ <- {
              if (job.strategy.maxResources <= plan.maxPages) {
                logger.info("Got payment confirmation. Running job " + job.id)
                job.run()
              } else {
                val s = s"Plan did not match expected size. The job's maxpages is: ${job.strategy.maxResources}. The plan's is: ${plan.maxPages}"
                logger.error(s)
                Future.failed(new Exception(s))
              }
            }
          } yield { Ok } */

       case plan: CreditPlan =>
         logger.info(s"""id=${idString} action=payment-confirmed plan=${plan.fastSpringKey} message="Received payment confirmation from FastSpring" """)
         for {
           userId <- Future(UserId(idString))
           _ <- {
             model.User.updateCredits(userId, plan.credits).map(_ =>
               logger.info(s"""id=${userId} action=credits-update amount=${plan.credits} message="${plan.fastSpringKey} purchase" """)
             )
           }
         } yield {
           Metrics.purchases.purchased(plan)
           Ok
         }
      }

    }) getOrElse {
      Metrics.purchases.callbackFailure()
      val s = "Error parsing order: \n" + req.body.asFormUrlEncoded.toString
      logger.error(s)
      InternalServerError(s)
    }
  }

}
