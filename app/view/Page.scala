package org.w3.vs.view

import play.api.mvc.Request

case class Page[A](private val iterable: Iterable[A])(implicit req: Request[_]) {

  val defaultPerPage = 50
  val maxPerPage = 1000

  val perPage: Int = {
    try {
      req.getQueryString("n").get.toInt match {
        case p if (p < 1) => defaultPerPage
        case p if (p > maxPerPage) => maxPerPage
        case p => p
      }
    } catch {
      case _ => defaultPerPage
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

  val iterator: Iterable[A] = {
    val offset = (current - 1) * perPage
    iterable.slice(offset, offset + perPage)
  }

  val size: Int = iterator.size
  val totalSize: Int = iterable.size

  val firstIndex: Int = (current - 1) * perPage + 1
  val lastIndex: Int = scala.math.min(current * perPage, totalSize)
}
