package org.w3.vs.view

import play.api.mvc.Request

object Page {
  val defaultPerPage = 50
  val maxPerPage = 1000
}

case class Page[A <: View](private val iterable: Iterable[A])(implicit req: Request[_], ordering: ((String, Boolean)) => Ordering[A]) {

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

  val sortParam: (String, Boolean) = {
    req.getQueryString("sort").map(param =>
      if (param.startsWith("-"))
        (param.replaceFirst("-",""), false)
      else
        (param, true)
    ).getOrElse(("", true))
  }

  val iterator: Iterable[A] = {
    val offset = (current - 1) * perPage
    iterable.toSeq
      .sorted(ordering(sortParam))  //With((a: A, b: A) => a.compare(b, sortParam))
      .slice(offset, offset + perPage)
  }

  val size: Int = iterator.size
  val totalSize: Int = iterable.size

  val firstIndex: Int = (current - 1) * perPage + 1
  val lastIndex: Int = scala.math.min(current * perPage, totalSize)

  def isSortedBy(param: String, ascending: Boolean = true): Boolean = {
    val is = sortParam match {
      case (p, a) if(p == param && a == ascending) => true
      case _ => false
    }
    println(is)
    is
  }

  val queryString = new Object {
    def sortBy(param: String, ascending: Boolean = true): String = toString(perPage, current, (param, ascending))
    override def toString = toString(perPage, current, sortParam)
    def toString(perPage: Int, current: Int, sortParam: (String, Boolean)) = {
      List(
        if (perPage != Page.defaultPerPage) "n=" + perPage else "",
        sortParam match {
          case (a, true) if (a != "") => "sort=" + sortParam._1
          case (a, false) if (a != "") => "sort=-" + sortParam._1
          case _ => ""
        },
        if (current != 1) "p=" + current else ""
      ).filter(_ != "").mkString("?","&","")
    }
  }

}
