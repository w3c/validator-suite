package org.w3.vs.view.collection

import play.api.templates.Html
import play.api.mvc.Request
import play.api.libs.json.JsArray
import org.w3.vs.view.model.View

object Collection {

  val DefaultPerPage = 30

  val MaxPerPage = 200

}

trait Collection[+A] {

  case class Definition(param: String, isSortable: Boolean)

  case class QueryParameter(param: String, value: String)

  def isEmpty: Boolean

  def source: Iterable[A]

  def iterable: Iterable[A]

  def firstIndex: Int

  def lastIndex: Int

  def currentPage: Int

  def maxPage: Int

  def size: Int

  def totalSize: Int

  def filteredSize: Int

  def id: String

  def classe: String

  def definitions: Seq[Definition]

  def legend: String

  def emptyMessage: Html

  def sortBy(param: String, ascending: Boolean): this.type

  def isSortedBy(param: String, ascending: Boolean): Boolean

  def goToPage(page: Int): this.type

  def showPerPage(perPage: Int): this.type

  def search(search: String): this.type

  def filterOn(filter: String): this.type

  def isFilteredOn(filter: String): Boolean

  def orderBy(order: String): this.type

  def offsetBy(offset: Int): this.type

  def queryParameters: Seq[QueryParameter]

  def queryString: String

  def bindFromRequest(implicit request: Request[_]): this.type

  def toJson: JsArray

}

abstract class CollectionImpl[A <: View]() extends Collection[A] {

  case class SortParam(param: String, ascending: Boolean)

  protected def filter(filter: Option[String]): A => Boolean

  protected def search(search: Option[String]): A => Boolean

  protected def order(order: Option[SortParam]): Ordering[A]

  case class CollectionParams(
    filter: Option[String] = None,
    search: Option[String] = None,
    group: Option[String] = None,
    sortParam: Option[SortParam] = None,
    page: Int = 1,
    perPage: Int = Collection.DefaultPerPage,
    offset: Int = 0
  )

  protected def params: CollectionParams = CollectionParams()

  private lazy val filtered: Seq[A] = {
    source.toSeq
      .filter{
        a => filter(params.filter)(a) && search(params.search)(a)
      }
  }

  // only val
  lazy val iterable: Seq[A] = {
    filtered
      .sorted(order(params.sortParam))
      .slice(params.offset, params.offset + params.perPage)
  }

  import scala.math

  def size: Int = iterable.size

  def totalSize: Int = source.size

  def filteredSize: Int = filtered.size

  def firstIndex: Int = math.min(params.offset + 1, totalSize) // if (filtered.isEmpty) 0 else offset + 1

  def lastIndex: Int = math.min(params.offset + params.perPage, totalSize)

  def maxPage: Int = math.max(math.ceil(filtered.size.toDouble / params.perPage.toDouble).toInt, 1)

  def queryString: String = {
    List(
      if (params.perPage != Collection.DefaultPerPage) "n=" + params.perPage else "",
      params.sortParam match {
        //case a if (a == ordering.default) => ""
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

  def isEmpty: Boolean = false

  def currentPage: Int = params.page

  def bindFromRequest(implicit request: play.api.mvc.Request[_]): this.type = this

  def filterOn(filter: String): this.type = this

  def goToPage(page: Int): this.type = this

  def isSortedBy(param: String, ascending: Boolean): Boolean = false

  def offsetBy(offset: Int): this.type = this

  def orderBy(order: String): this.type = this

  def queryParameters: Seq[QueryParameter] = Seq.empty

  def search(search: String): this.type = this

  def showPerPage(perPage: Int): this.type = this

  def sortBy(param: String,ascending: Boolean): this.type = this

  def legend: String = ""

  def isFilteredOn(filter: String): Boolean = false

  def toJson: JsArray = {
    JsArray(iterable.map(a => a.toJson(Some(this))))
  }

}
