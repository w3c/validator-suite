package org.w3.vs.view

import play.api.mvc.Request

object Page {
  val defaultPerPage = 50
  val maxPerPage = 1000

  def apply[A <: View](a: A)(implicit req: Request[_], ordering: PageOrdering[A]): Page[A] = Page(Iterable(a))

}

case class Page[A <: View](private val iterable: Iterable[A])(implicit req: Request[_], ordering: PageOrdering[A]) {

  val perPage: Int = {
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

  val maxPage: Int = {
    scala.math.ceil(iterable.size.toDouble / perPage.toDouble).toInt
  }

  val current: Int =  {
    try {
      req.getQueryString("p").get.toInt  match {
        case p if (p < 1) => 1
        case p if (p > maxPage)=> maxPage
        case p => p
      }
    } catch {case _ => 1}
  }

  val sortParam: SortParam = {
    req.queryString.get("sort").flatten.headOption.map(param =>
      if (param.startsWith("-"))
        ordering.validate(SortParam(param.replaceFirst("-",""), true))
      else
        ordering.validate(SortParam(param, false))
    ).getOrElse(ordering.default)
  }

  val iterator: Iterable[A] = {
    val offset = (current - 1) * perPage
    iterable.toSeq
      .sorted(ordering.ordering(sortParam))
      .slice(offset, offset + perPage)
  }

  val size: Int = iterator.size
  val totalSize: Int = iterable.size

  val firstIndex: Int = (current - 1) * perPage + 1
  val lastIndex: Int = scala.math.min(current * perPage, totalSize)

  def isSortedBy(param: String, ascending: Boolean = true): Boolean = {
    sortParam match {
      case SortParam(p, a) if(p == param && a == ascending) => true
      case _ => false
    }
  }

  val queryString = new Object {
    def sortBy(param: String, ascending: Boolean = true): String = toString(perPage, current, SortParam(param, ascending))
    override def toString = toString(perPage, current, sortParam)
    def toString(perPage: Int, current: Int, sortParam: SortParam) = {
      List(
        if (perPage != Page.defaultPerPage) "n=" + perPage else "",
        sortParam match {
          case a if (a == ordering.default) => ""
          case SortParam(a, true) if (a != "") => "sort=-" + sortParam.name
          case SortParam(a, false) if (a != "") => "sort=" + sortParam.name
          case _ => ""
        },
        if (current != 1) "p=" + current else ""
      ).filter(_ != "").mkString("?","&","")
    }
  }

  val defaultSort: SortParam = ordering.default

}
