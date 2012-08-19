package org.w3.vs.view

import play.api.mvc.Request

object Page {
  val defaultPerPage = 50
  val maxPerPage = 1000

  def apply[A <: View](a: A)(implicit req: Request[_], ordering: PageOrdering[A], filtering: PageFiltering[A]): Page[A] = new Page(Iterable(a))
  def apply[A <: View](i: Iterable[A])(implicit req: Request[_], ordering: PageOrdering[A], filtering: PageFiltering[A]): Page[A] = new Page(i)

}

class Page[A <: View](private val iterable: Iterable[A])(implicit req: Request[_], ordering: PageOrdering[A], filtering: PageFiltering[A]) {

  def size: Int = iterator.size
  def totalSize: Int = iterable.size

  def perPage: Int = {
    try {
      req.getQueryString("n").get.toInt match {
        case p if (p < 1) => Page.defaultPerPage
        case p if (p > Page.maxPerPage) => Page.maxPerPage
        case p => p
      }
    } catch {
      case _ => Page.defaultPerPage
    }
  }

  def maxPage: Int = {
    scala.math.ceil(iterable.size.toDouble / perPage.toDouble).toInt
  }

  def current: Int =  {
    try {
      req.getQueryString("p").get.toInt  match {
        case p if (p < 1) => 1
        case p if (p > maxPage)=> maxPage
        case p => p
      }
    } catch {case _ => 1}
  }

  def firstIndex: Int = (current - 1) * perPage + 1
  def lastIndex: Int = scala.math.min(current * perPage, totalSize)

  def defaultSortParam: SortParam = ordering.default

  def sortParam: SortParam = {
    req.queryString.get("sort").flatten.headOption.map(param =>
      if (param.startsWith("-"))
        ordering.validate(SortParam(param.replaceFirst("-",""), true))
      else
        ordering.validate(SortParam(param, false))
    ).getOrElse(ordering.default)
  }

  def filter: Option[String] = {
    try {
      req.getQueryString("filter").get.toString match {
        case a => Some(a)
      }
    } catch {
      case _ => None
    }
  }

  //val size: Int = iterator.size
  //val totalSize: Int = iterable.size

  //val firstIndex: Int = (current - 1) * perPage + 1
  //val lastIndex: Int = scala.math.min(current * perPage, totalSize)

  def isSortedBy(param: String, ascending: Boolean = true): Boolean = {
    sortParam match {
      case SortParam(p, a) if(p == param && a == ascending) => true
      case _ => false
    }
  }

  def queryString = QueryString(current, perPage, sortParam, filter)

  case class QueryString(page: Int, perPage: Int, sortParam: SortParam, filter: Option[String]) {
    override def toString = {
      List(
        if (perPage != Page.defaultPerPage) "n=" + perPage else "",
        sortParam match {
          case a if (a == ordering.default) => ""
          case SortParam(a, true) if (a != "") => "sort=-" + sortParam.name
          case SortParam(a, false) if (a != "") => "sort=" + sortParam.name
          case _ => ""
        },
        if (filter!= None) "filter=" + filter.get else "",
        if (page!= 1) "p=" + page else ""
      ).filter(_ != "").mkString("?","&","")
    }
    def sortBy(param: String, ascending: Boolean = true) = this.copy(sortParam = SortParam(param, ascending))
    def filterBy(param: Option[String]) = this.copy(filter = param)
    def goToPage(page: Int) = this.copy(page = page)
  }

  def iterator: Iterable[A] = {
    val offset = (current - 1) * perPage
    iterable.toSeq
      .filter(filtering.filter(filter))
      .sorted(ordering.order(sortParam))
      .slice(offset, offset + perPage)
  }

}
