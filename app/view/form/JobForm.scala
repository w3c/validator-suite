package org.w3.vs.view.form

import org.w3.vs.web.URL
import org.w3.vs.{Global, ValidatorSuite}
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format._
import play.api.mvc.{Filter => _, _}
import scala.concurrent._

import play.api.i18n.Messages
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object JobForm {

  import play.api.data.validation.Constraints._
  import Global.vs

  def apply(user: User): Form[Job] = Form(
    mapping(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL].verifying("invalid", { url =>
        try {
          // careful: this is blocking IO, potentially up to 10 seconds
          val code = vs.formHttpClient.prepareGet(url.toString).execute().get(10, TimeUnit.SECONDS).getStatusCode
          code == 200
        } catch { case e: Exception =>
          false
        }
      }),
      "maxPages" -> of[Int].verifying(min(1)).verifying("creditMaxExceeded", { credits =>
        credits <= user.credits
      })
    )((name, entrypoint, maxPages) => {
      val strategy = Strategy(URL(entrypoint), maxPages)
      Job(name = name, strategy = strategy, creatorId = Some(user.id))
    })((job: Job) =>
      Some(job.name, job.strategy.entrypoint, job.strategy.maxResources)
    )
  )

}
