package org.w3.vs.view.form

import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data._
import play.api.mvc._
import play.api.i18n.Messages
import org.w3.util.URL
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import org.w3.vs.controllers._
import scala.concurrent._
import java.util.concurrent.TimeoutException
import org.w3.vs.assertor.Assertor
import org.w3.banana._

object JobForm {

  def assertors()(implicit req: Request[AnyContent]): Seq[Assertor] = try {
    req.body.asFormUrlEncoded.get.get("assertor[]").get.map(Assertor.get)
  } catch { case _: Exception =>
    Seq.empty
  }

  def assertorParameters()(implicit req: Request[AnyContent]): AssertorsConfiguration = {
    assertors().map { assertor =>
      val k = assertor.id
      val v: Map[String, List[String]] = req.body.asFormUrlEncoded match {
        case Some(foo) => foo.collect {
          case (param, values) if (param.startsWith(assertor.id + "-")) =>
            (param.replaceFirst("^" + assertor.id + "-", ""), values.toList)
        }.toMap
        case None => Map.empty
      }
      (k -> v)
    }.toMap
  }

//    assertors().toList.map(s => Assertor.get(s).id).foldLeft(Map[AssertorId, Map[String, List[String]]]()){ (m, assertor) => m.+(
//      (assertor -> req.body.asFormUrlEncoded.flatten.collect{
//        case a if (a._1.startsWith(assertor.id + "-")) => a.copy(_1 = a._1.replaceFirst("^" + assertor.id + "-", ""))
//      }.toMap))
//    }
//  }



  /*def hasParam(param: String)(implicit req: Request[AnyContent]): Boolean = {
    try {
      req.body.asFormUrlEncoded.get.get(param).get
      true
    } catch { case _ =>
      false
    }
  }

  def hasParam(param: String, value: String)(implicit req: Request[AnyContent]): Boolean = {
    try {
      req.body.asFormUrlEncoded.get.get(param).get.contains(value)
    } catch { case _ =>
      false
    }
  }*/

  def bind()(implicit req: Request[AnyContent], context: ExecutionContext): Future[Either[JobForm, ValidJobForm]] = {

    //println(assertorParameters())

    val form: Form[(String, URL, Boolean, Int)] = playForm.bindFromRequest

    val vsform: Either[JobForm, ValidJobForm] = form.fold(
      f => Left(new JobForm(f, assertorParameters())),
      s => {
        if (assertors().isEmpty)
          Left(new JobForm(form.withError("assertor", "No assertor selected", "error"), assertorParameters())) // TODO
        else
          Right(new ValidJobForm(form, s, assertorParameters()))
      }
    )

    vsform.asFuture
  }

  def blank: JobForm = new JobForm(playForm, AssertorsConfiguration.default)

  def fill(job: Job) = new ValidJobForm(
    playForm fill(
      job.name,
      job.strategy.entrypoint,
      job.strategy.linkCheck,
      job.strategy.maxResources
    ), (
      job.name,
      job.strategy.entrypoint,
      job.strategy.linkCheck,
      job.strategy.maxResources
    ), job.vo.strategy.assertorsConfiguration
  )

  private def playForm: Form[(String, URL, Boolean, Int)] = Form(
    tuple(
      "name" -> nonEmptyText,
      //"assertor" -> of[Seq[String]].verifying("Choose an assertor", ! _.isEmpty),
      "entrypoint" -> of[URL],
      "linkCheck" -> of[Boolean](booleanFormatter),
      "maxResources" -> number(min=1, max=500)
    )
  )

}

class JobForm private[view](
    form: Form[(String, URL, Boolean, Int)],
    val assertorsConfiguration: AssertorsConfiguration) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{case error => ("error", /*error.key + */error.message)}

  def hasAssertor(assertor: String): Boolean = try {
    assertorsConfiguration.contains(AssertorId(assertor))
  } catch { case _: Exception =>
    false
  }

}

class ValidJobForm private[view](
    form: Form[(String, URL, Boolean, Int)],
    bind: (String, URL, Boolean, Int),
    assertorsConfiguration: AssertorsConfiguration) extends JobForm(form, assertorsConfiguration) with VSForm {

  val (name, entrypoint, linkCheck, maxResources) = bind

  def createJob(user: User)(implicit conf: VSConfiguration): Job = {
    Job(
      name = name,
      organization = user.vo.organization.get, // TODO what if organization = None?
      creator = user.id,
      strategy = Strategy(
        entrypoint = entrypoint,
        linkCheck = linkCheck,
        filter = Filter.includePrefix(entrypoint.toString), // Tom: non persisté de toute façon
        maxResources = maxResources,
        assertorsConfiguration = assertorsConfiguration)
)
  }

  def update(job: Job)(implicit conf: VSConfiguration): Job = {
    null // TODO decide, implement
    //     job.copy(
    //         name = name,
    //         strategy = job.strategy.copy(
    //             entrypoint = url,
    //             linkCheck = linkCheck,
    //             maxResources = maxResources
    //         )
    //     )
  }

}
