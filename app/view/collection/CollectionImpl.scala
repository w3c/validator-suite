package org.w3.vs.view.collection

import org.w3.vs.view.Collection._
import org.w3.vs.view._
import play.api.i18n.Messages
import play.api.libs.json.{JsValue, Json}
import play.api.templates.Html
import scala.math
import scalaz.Scalaz._
import play.api.mvc.RequestHeader
import java.net.URLEncoder

abstract class CollectionImpl[A <: Model] extends Collection[A] {

  protected def filter(filter: Option[String]): A => Boolean

  protected def search(search: Option[String]): A => Boolean

  protected def order(order: SortParam): Ordering[A]

  protected def copyWith(params: Parameters): Collection[A]

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

  def size: Int = iterable.size

  def totalSize: Int = source.size

  def filteredSize: Int = filtered.size

  def firstIndex: Int = math.min(params.offset + 1, filteredSize) // if (filtered.isEmpty) 0 else offset + 1

  def lastIndex: Int = math.min(params.offset + params.perPage, filteredSize)

  def page: Int = params.page

  def maxPage: Int = math.max(math.ceil(filtered.size.toDouble / params.perPage.toDouble).toInt, 1)

  def sortBy(param: String, ascending: Boolean): Collection[A] =
    copyWith(params.copy(sortParam = Some(SortParam(param, ascending))))

  def filterOn(filter: String): Collection[A] =
    copyWith(params.copy(filter = if (filter != "") Some(filter) else None))

  def search(search: String): Collection[A] =
    copyWith(params.copy(search = if (search != "") Some(search) else None))

  def groupBy(group: String): Collection[A] =
    copyWith(params.copy(group = if (group != "") Some(group) else None))

  def goToPage(_page: Int): Collection[A] = {
    val page = _page match {
      case p if p > maxPage => maxPage
      case p if p < 1 => 1
      case p => p
    }
    val offset = (page - 1) * params.perPage
    copyWith(params.copy(page = page, offset = offset))
  }

  def showPerPage(perPage: Int): Collection[A] =
    copyWith(params.copy(perPage = if (perPage > MaxPerPage || perPage < 1) MaxPerPage else perPage))

  def offsetBy(_offset: Int): Collection[A] = {
    val offset = if (_offset < 0) 0 else _offset
    val page = math.max(maxPage, math.ceil(offset.toDouble / params.perPage.toDouble).toInt)
    copyWith(params.copy(
      offset = offset,
      page = page
    ))
  }

  def bindFromRequest(implicit req: RequestHeader): Collection[A] = {
    var res: Collection[A] = this
    res = req.getQueryString("n").map(n => res.showPerPage(n.toInt)).getOrElse(res)
    res = req.getQueryString("filter").map(res.filterOn(_)).getOrElse(res)
    res = req.getQueryString("search").map(res.search(_)).getOrElse(res)
    //res = req.getQueryString("group").map(res.groupBy(_)).getOrElse(res)
    res = req.getQueryString("sort").map(sort => res.sortBy(sort.replaceFirst("^-",""), sort.startsWith("-"))).getOrElse(res)
    res = req.getQueryString("p").map(p => res.goToPage(p.toInt)).getOrElse(res)
    res = req.getQueryString("offset").map(offset => res.offsetBy(offset.toInt)).getOrElse(res)
    res
  }

  def isEmpty: Boolean = iterable.isEmpty

  def isFilteredOn(filter: String): Boolean =
    params.filter === Some(filter)

  def isSortedBy(param: String, ascending: Boolean): Boolean = {
    params.sortParam === Some(SortParam(param, ascending)) ||
      params.sortParam === None && SortParam(param, ascending) === defaultSortParam
  }

  def isGroupedBy(group: String): Boolean =
    params.group === Some(group)


  def queryParameters: Seq[QueryParameter] = {
    Seq (
      params.resource match {
        case Some(url) => Some(QueryParameter("resource", URLEncoder.encode(url.toString, "UTF-8")))
        case _ => None
      },
      if (params.perPage != DefaultPerPage) Some(QueryParameter("n", params.perPage.toString)) else None,
      params.sortParam match {
        case Some(sort) if (sort === defaultSortParam) => None
        case Some(SortParam(a, true)) => Some(QueryParameter("sort", "-" + a))
        case Some(SortParam(a, false)) => Some(QueryParameter("sort", a))
        case _ => None
      },
      params.filter.map(QueryParameter("filter", _)),
      params.search.map(QueryParameter("search", _)),
      if (params.page != 1) Some(QueryParameter("p", params.page.toString)) else None,
      params.group match {
        case Some(a) if a != "url" => Some(QueryParameter("group", a))
        case _ => None
      }
    ).collect{case Some(queryParam) => queryParam}
  }

  def queryString: String =
    queryParameters.map(q => q.name + "=" + q.value).mkString("?","&","")

  def legend: String = {
    if(size > 0) {
      if (filteredSize == 1)
        Messages("pagination.legend.one")
      else
        Messages("pagination.legend", firstIndex, lastIndex, filteredSize)
    } else {
      Messages("pagination.empty")
    }
  }

//  def toJson()(implicit format: Format[A]): JsValue =
//    Json.toJson(iterable.map(o => Json.toJson(o)))

  //protected def toJson(a: A): JsValue

  def toJson: JsValue =
    Json.toJson(iterable.map(_.toJson))

  def toHtml: Html =
    views.html.collection.generic(this)

  //def attributes: Iterable[Attribute] = Iterable.empty

  def attributes: Iterable[Attribute] = Iterable(
    ("url" -> route.toString),
    ("count" -> source.size.toString)
  ).map(a => Attribute(a._1, a._2))

}
