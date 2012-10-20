package org.w3.vs.view.collection

import play.api.templates.Html
import play.api.libs.json.JsArray
import org.w3.vs.view.model.View
import scalaz.Scalaz._
import scalaz.Equal
import play.api.i18n.Messages

object Collection {

  val DefaultPerPage = 30

  val MaxPerPage = 200

  case class Definition(param: String, isSortable: Boolean)

  case class QueryParameter(param: String, value: String)

  case class SortParam(param: String, ascending: Boolean)

  case class Parameters(
    sortParam: Option[SortParam] = None,
    filter: Option[String] = None,
    search: Option[String] = None,
    group: Option[String] = None,
    page: Int = 1,
    perPage: Int = DefaultPerPage,
    offset: Int = 0
  )

  implicit val equal: Equal[SortParam] = new Equal[SortParam] {
    def equal(a1: SortParam, a2: SortParam): Boolean = {
      a1 == a2
    }
  }

}

trait Collection[+A] {

  import Collection._

  def defaultSortParam: SortParam

  def params: Parameters

  def isEmpty: Boolean

  def source: Iterable[A]

  def iterable: Iterable[A]

  def firstIndex: Int

  def lastIndex: Int

  def page: Int

  def maxPage: Int

  def size: Int

  def totalSize: Int

  def filteredSize: Int

  def id: String

  def classe: String

  def definitions: Seq[Definition]

  def legend: String

  def emptyMessage: Html

  def isSortedBy(param: String, ascending: Boolean): Boolean

  def sortBy(param: String, ascending: Boolean): Collection[A]

  def goToPage(page: Int): Collection[A]

  def showPerPage(perPage: Int): Collection[A]

  def search(search: String): Collection[A]

  def filterOn(filter: String): Collection[A]

  def offsetBy(offset: Int): Collection[A]

  def isFilteredOn(filter: String): Boolean

  def queryParameters: Seq[QueryParameter]

  def queryString: String

  def toJson: JsArray

  def toHtml: Seq[Html]

  def template: Option[Html]

}

abstract class CollectionImpl[A <: View] extends Collection[A] {

  import Collection._

  protected def filter(filter: Option[String]): A => Boolean

  protected def search(search: Option[String]): A => Boolean

  protected def order(order: SortParam): Ordering[A]

  protected def copyWith(params: Parameters): Collection[A]

 // def bindFromRequest(implicit request: Request[_]): CollectionImpl[A]

  private lazy val filtered: Seq[A] = {
    source.toSeq
      .filter{
        a => filter(params.filter)(a) && search(params.search)(a)
      }
  }

  lazy val iterable: Seq[A] = {
    filtered
      .sorted(order(params.sortParam.getOrElse(defaultSortParam)))
      .slice(params.offset, params.offset + params.perPage)
  }

  import scala.math

  def size: Int = iterable.size

  def totalSize: Int = source.size

  def filteredSize: Int = filtered.size

  def firstIndex: Int = math.min(params.offset + 1, totalSize) // if (filtered.isEmpty) 0 else offset + 1

  def lastIndex: Int = math.min(params.offset + params.perPage, totalSize)

  def page: Int = params.page

  def maxPage: Int = math.ceil(filtered.size.toDouble / params.perPage.toDouble).toInt

  def isEmpty: Boolean = iterable.isEmpty

  def bindFromRequest(implicit req: play.api.mvc.Request[_]): Collection[A] = {
    val page    = try { req.getQueryString("p").get.toInt } catch { case _: Exception => 1 }
    val perPage = try { req.getQueryString("n").get.toInt } catch { case _: Exception => Collection.DefaultPerPage }
    val filter = req.getQueryString("filter")
    val search = req.getQueryString("search")
    val group = req.getQueryString("group")
    val sort: Option[SortParam] =
      try {
        req.queryString.get("sort").toSeq.flatten.head match {
          case param if (param.startsWith("-")) => Some(SortParam(param.replaceFirst("-",""), true))
          case param => Some(SortParam(param, false))
        }
      } catch {
        case _: Exception => Some(defaultSortParam)
      }

    val res = copyWith(Collection.Parameters(
      sortParam = sort,
      filter = filter,
      search = search,
      group = group,
      perPage = perPage
    ))

    if (req.getQueryString("offset").isDefined)
      res.offsetBy(req.getQueryString("offset").get.toInt)
    else
      res.goToPage(page)
  }

  def filterOn(filter: String): Collection[A] =
    copyWith(params.copy(filter = if (filter != "") Some(filter) else None))

  def goToPage(_page: Int): Collection[A] = {
    val page = _page match {
      case p if p > maxPage => maxPage
      case p if p < 1 => 1
      case p => p
    }
    val offset = (page - 1) * params.perPage
    copyWith(params.copy(page = page, offset = offset))
  }

  def isSortedBy(param: String, ascending: Boolean): Boolean =
    params.sortParam === Some(SortParam(param, ascending))

  def offsetBy(_offset: Int): Collection[A] = {
    val offset = if (_offset < 0) 0 else _offset
    val page = math.max(maxPage, math.ceil(offset.toDouble / params.perPage.toDouble).toInt)
    copyWith(params.copy(
      offset = offset,
      page = page
    ))
  }

  def queryParameters: Seq[QueryParameter] = Seq.empty

  def queryString: String = {
    List(
      if (params.perPage != DefaultPerPage) "n=" + params.perPage else "",
      params.sortParam match {
        case Some(sort) if (sort === defaultSortParam) => ""
        case Some(SortParam(a, true)) => "sort=-" + a
        case Some(SortParam(a, false)) => "sort=" + a
        case _ => ""
      },
      if (params.filter != None) "filter=" + params.filter.get else "",
      if (params.search != None) "search=" + params.search.get else "",
      if (params.page != 1) "p=" + params.page else "",
      if (params.group != None && params.group != Some("url")) "group=" + params.group.get else ""
    ).filter(_ != "").mkString("?","&","")
  }

  def search(search: String): Collection[A] =
    copyWith(params.copy(search = if (search != "") Some(search) else None))

  def showPerPage(perPage: Int): Collection[A] =
    copyWith(params.copy(perPage = if (perPage > MaxPerPage || perPage < 1) MaxPerPage else perPage))

  def sortBy(param: String, ascending: Boolean): Collection[A] =
    copyWith(params.copy(sortParam = Some(SortParam(param, ascending))))

  def legend: String = {
    if(size > 0) {
      Messages("pagination.legend", firstIndex, lastIndex, totalSize)
    } else {
      Messages("pagination.empty")
    }
  }

  def isFilteredOn(filter: String): Boolean =
    params.filter === Some(filter)

  def toJson: JsArray = {
    JsArray(iterable.map(_.toJson))
  }

  def toHtml: Seq[Html] = {
    iterable.map(_.toHtml)
  }



}
