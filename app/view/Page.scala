package org.w3.vs.view

import play.api.mvc.Request

case class Page[A <: View] private (
    iterable: Iterable[A],
    current: Int = 1,                             // p=
    perPage: Int = Page.defaultPerPage,           // n=
    filter: Option[String] = None,                // filter=
    search: Option[String] = None,                // search=
    sortParam: SortParam = SortParam("", true),   // sort=
    group: Option[String] = None)(                // group=
      implicit val ordering: PageOrdering[A],
      val filtering: PageFiltering[A]) {

  private val offset = (current - 1) * perPage

  private val filtered: Seq[A] = {
    iterable.toSeq
      .filter(filtering.filter(filter))
      .filter(filtering.search(search)) // compose functions instead
  }

  val iterator: Seq[A] = {
    filtered
      .sorted(ordering.order(sortParam))
      .slice(offset, offset + perPage)
  }

  val size: Int = iterator.size

  val totalSize: Int = filtered.size

  val firstIndex: Int = scala.math.min(offset + 1, totalSize) // if (filtered.isEmpty) 0 else offset + 1

  val lastIndex: Int = scala.math.min(offset + perPage, totalSize)

  val maxPage: Int = scala.math.ceil(filtered.size.toDouble / perPage.toDouble).toInt

  def goToPage(page: Int): Page[A] = {
    page match {
      case p if p == current => this
      case p if p > maxPage => this.copy(current = maxPage)
      case p if p < 1 => this.copy(current = 1)
      case p => this.copy(current = p)
    }
  }

  def show(perPage: Int): Page[A] = {
    perPage match {
      case n if n > Page.maxPerPage => this.copy(perPage = Page.maxPerPage)
      case n if n < 1 => this.copy(perPage = 1)
      case n => this.copy(perPage = n)
    }
  }

  def sortBy(sort: SortParam): Page[A] = this.copy(sortParam = ordering.validate(sort))

  def search(search: Option[String]): Page[A] = this.copy(search = search)

  def filterBy(filter: Option[String]): Page[A] = this.copy(filter = filtering.validate(filter))

  def groupBy(group: Option[String]): Page[A] = this.copy(group = group)

  val queryString: String = {
    List(
      if (perPage != Page.defaultPerPage) "n=" + perPage else "",
      sortParam match {
        case a if (a == ordering.default) => ""
        case SortParam(a, true) if (a != "") => "sort=-" + sortParam.name
        case SortParam(a, false) if (a != "") => "sort=" + sortParam.name
        case _ => ""
      },
      if (filter != None) "filter=" + filter.get else "",
      if (search != None) "search=" + search.get else "",
      if (current != 1) "p=" + current else "",
      if (group != None && group != Some("url")) "group=" + group.get else ""
    ).filter(_ != "").mkString("?","&","")
  }

}

object Page {
  val defaultPerPage = 50
  val maxPerPage = 1000

  def apply[A <: View](a: A)(implicit req: Request[_], ordering: PageOrdering[A], filtering: PageFiltering[A]): Page[A] = {
    apply(Iterable(a))
  }

  def apply[A <: View](a: Iterable[A])(
      implicit req: Request[_],
      ordering: PageOrdering[A],
      filtering: PageFiltering[A]): Page[A] = {

    val page    = try { req.getQueryString("p").get.toInt } catch { case _ => 1 }
    val perPage = try { req.getQueryString("n").get.toInt } catch { case _ => Page.defaultPerPage }
    val filter = req.getQueryString("filter")
    val search = req.getQueryString("search")
    val group = req.getQueryString("group")
    val sort =
      try {
        req.queryString.get("sort").flatten.head match {
          case param if (param.startsWith("-")) => SortParam(param.replaceFirst("-",""), true)
          case param => SortParam(param, false)
        }
      } catch {
        case _ => ordering.default
      }

    new Page[A](a)
      .show(perPage)
      .sortBy(sort)
      .filterBy(filter)
      .search(search)
      .groupBy(group)
      .goToPage(page)

  }
}