package org.w3.vs.view

import play.api.templates.Html
import scalaz.Equal
import play.api.mvc.{RequestHeader, Call}
import org.w3.vs.web.URL

trait Collection[+A] extends View {

  import Collection._

  def params: Parameters

  def source: Iterable[A]

  def iterable: Iterable[A]

  def size: Int

  def totalSize: Int

  def filteredSize: Int

  def firstIndex: Int

  def lastIndex: Int

  def page: Int

  def maxPage: Int

  def sortBy(param: String, ascending: Boolean): Collection[A]

  def filterOn(filter: String): Collection[A]

  def search(search: String = ""): Collection[A]

  def groupBy(group: String): Collection[A]

  def goToPage(page: Int): Collection[A]

  def showPerPage(perPage: Int): Collection[A]

  def offsetBy(offset: Int): Collection[A]

  def bindFromRequest(implicit req: RequestHeader): Collection[A]

  def isEmpty: Boolean

  def isFilteredOn(filter: String): Boolean

  def isSortedBy(param: String, ascending: Boolean): Boolean

  def isGroupedBy(group: String): Boolean

  def route: Call

  def queryParameters: Seq[QueryParameter]

  def queryString: String

  def legend: String

  def id: String

  def classe: String

  def attributes: Iterable[Attribute]

  def definitions: Seq[Definition]

  def defaultSortParam: SortParam

  def emptyMessage: Html

  def jsTemplate: Option[Html]

}

object Collection {

  val DefaultPerPage = 30

  val MaxPerPage = 500

  case class Definition(name: String, isSortable: Boolean)

  case class QueryParameter(name: String, value: String)

  case class Attribute(name: String, value: String)

  case class SortParam(name: String, ascending: Boolean)

  case class Parameters(
    sortParam: Option[SortParam] = None,
    filter: Option[String] = None,
    search: Option[String] = None,
    group: Option[String] = None,
    page: Int = 1,
    perPage: Int = DefaultPerPage,
    offset: Int = 0,
    resource: Option[URL] = None
  )

  implicit val equal: Equal[SortParam] = new Equal[SortParam] {
    def equal(a1: SortParam, a2: SortParam): Boolean = {
      a1 == a2
    }
  }

/*  implicit def queryStringBinder(implicit intBinder: QueryStringBindable[Int]) = new QueryStringBindable[Pager] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Pager]] = {
      for {
        index <- intBinder.bind(key + ".index", params)
        size <- intBinder.bind(key + ".size", params)
      } yield {
        (index, size) match {
          case (Right(index), Right(size)) => Right(Pager(index, size))
          case _ => Left("Unable to bind a Pager")
        }
      }
    }
    override def unbind(key: String, pager: Pager): String = {
      intBinder.unbind(key + ".index", pager.index) + "&" + intBinder.unbind(key + ".size", pager.size)
    }
  }*/

}