package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.util._
import org.w3.vs.model._

case class ResourceView(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends View

object ResourceView {
  def fromAssertions(assertions: Iterable[Assertion]): Iterable[ResourceView] = {
    /*assertions.groupBy(_.url) map { case (url, as) =>
      val last = as.maxBy(_.timestamp).timestamp
      var errors = 0
      var warnings = 0
      as foreach { a =>
        a.severity match {
          case Error => errors += math.max(1, a.contexts.size)
          case Warning => warnings += math.max(1, a.contexts.size)
          case Info => ()
        }
      }
      ResourceView(url, last, warnings, errors)
    }*/
    assertions.groupBy(_.url).map{ case (url, assertions) => {
      val last = assertions.maxBy(_.timestamp).timestamp
      val errors = assertions.foldLeft(0){case (count, assertion) =>
        count + (assertion.severity match {
          case Error => scala.math.max(assertion.contexts.size, 1)
          case _ => 0
        })
      }
      val warnings = assertions.foldLeft(0){case (count, assertion) =>
        count + (assertion.severity match {
          case Warning => scala.math.max(assertion.contexts.size, 1)
          case _ => 0
        })
      }
      ResourceView(url, last, warnings, errors)
    }}
  }

  /*def fromJob(job: Job): FutureVal[Exception, Iterable[ResourceView]] = {
    job.getAssertions() map {
      _.groupBy(_.url) map { case (url, as) =>
        val last = as.maxBy(_.timestamp).timestamp
        var errors = 0
        var warnings = 0
        as foreach { a =>
          a.severity match {
            case Error => errors += math.max(1, a.contexts.size)
            case Warning => warnings += math.max(1, a.contexts.size)
            case Info => ()
          }
        }
        ResourceView(url, last, warnings, errors)
      }
    }
  }*/

  val params = Seq[String](
    "url",
    "validated",
    "warnings",
    "errors"
  )

}