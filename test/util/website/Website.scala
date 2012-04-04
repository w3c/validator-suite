package org.w3.util.website

import unfiltered.request._
import unfiltered.response._

/** A website made of a sequence of declarative [[org.w3.vs.website.Link]]s
  *
  * @param links
  */
case class Website(links:Iterable[Link]) {
  
  /** compiles this website to an Unfiltered plan */
  def toPlanify = unfiltered.filter.Planify {
    case Path(path) if path startsWith "/404" =>
      NotFound
    case Path(path) => {
      val outLinks = links flatMap { _.outlinksFor(path) }
      val webpage = Webpage(path, outLinks)
      Ok ~> ContentType("text/html") ~> ResponseString(webpage.toHtml)
    }
  }
  
}

object Website {
  
  def cyclic(size: Int): Website = {
    var links = 1 to (size -1) map { case i => ("/"+i) --> ("/"+(i+1)) }
    links = ("/" + size.toString --> "/1") +: ("/" --> "/1") +: links
    Website(links)
  }
  
  def tree(width: Int): Website = {
    Website(Seq("""((/\d)*/)""" ---> { case (url,_) => (1 to width) map { j => url + j.toString + "/" } }))
  }
}
