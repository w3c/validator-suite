package org.w3.vs.view.collection

import org.w3.vs.model.{Job, JobId, AssertionSeverity}
import org.w3.vs.view.Collection._
import org.w3.vs.view.model.GroupedAssertionView
import play.api.templates.{HtmlFormat, Html}
import play.api.mvc.Call
import org.w3.vs.web.URL
import scalaz.Scalaz._
import play.api.i18n.Messages
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.store.Formats._
import play.api.libs.json.JsValue
import controllers.routes
import org.w3.vs._

case class GroupedAssertionsView(
  source: Iterable[GroupedAssertionView],
  route: Call,
  params: Parameters = Parameters()) extends CollectionImpl[GroupedAssertionView] {

  // html attributes
  val id = "assertions"
  val classe = "folds"

  def definitions = GroupedAssertionView.definitions

  def defaultSortParam = SortParam("", ascending = false)

  def order(sort: SortParam) = {
    val a = Ordering[AssertionSeverity].reverse
    val b = Ordering[Int].reverse
    val c = Ordering[String]
    Ordering.Tuple3(a, b, c).on[GroupedAssertionView](assertion => (assertion.severity, assertion.occurrences, assertion.title.body))
  }

  def filter(filter: Option[String]) =
    filter match {
      case Some(param) => {
        case assertion if (assertion.assertor.toString === param) => true
        case _ => false
      }
      case None => _ => true
    }

  def search(search: Option[String]): (GroupedAssertionView => Boolean) = {
    search match {
      case Some(search) => {
        case assertion if (assertion.title.toString.toLowerCase.contains(search.toLowerCase)) => true
        case _ => false
      }
      case None => _ => true
    }
  }

  def emptyMessage: Html = Html(Messages("assertions.empty"))

  def jsTemplate: Option[Html] = Some(views.html.template.groupedAssertion())

  def copyWith(params: Parameters) = copy(params = params)

}

object GroupedAssertionsView {

  def apply(job: Job)(implicit vs: ValidatorSuite with Database): Future[GroupedAssertionsView] = {
    job.getGroupedAssertionDatas().map(gAssertionsDatas =>
      GroupedAssertionsView(
        source = gAssertionsDatas.map(data => GroupedAssertionView(job.id, data)),
        route = routes.GroupedAssertions.index(job.id)
      )
    )
  }

  //  def apply(assertions: Iterable[Assertion], id: JobId, url: URL): AssertionsView = {
  //    AssertionsView(source = assertions.map(assertion => AssertionView(assertion, id)), route = routes.Assertions.index(id, Some(url)))
  //  }
  //
  //  def grouped(assertions: Iterable[Assertion], id: JobId): AssertionsView = {
  //    val now = DateTime.now()
  //    // group by title + assertorId
  //    val views = assertions.groupBy(e => e.title + e.assertor).map { case (_, assertions) =>
  //      // /!\ assuming that the severity is the same for all assertions sharing the same title.
  //      val assertorKey = assertions.head.assertor
  //      val severity = assertions.head.severity
  //      val title = HtmlFormat.raw(assertions.head.title)
  //      val description = None //assertions.head.description.map(HtmlFormat.raw)
  //      val resources = assertions.map(_.url.underlying).toSeq.sortBy(_.toString)
  //      val occurrences = assertions.foldLeft(0)((count, assertion) => count + scala.math.max(1, assertion.contexts.size))
  //      AssertionView(
  //        id = title.body.hashCode,
  //        jobId = id,
  //        assertor = assertorKey,
  //        severity = severity,
  //        validated = now,
  //        title = title,
  //        description = description,
  //        occurrences = occurrences,
  //        resources = resources
  //      )
  //    }
  //    AssertionsView(source = views, route = routes.Assertions.index(id, None))
  //  }

}
