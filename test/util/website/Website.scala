package org.w3.util.website

import javax.servlet.http._

/** A website made of a sequence of declarative [[org.w3.vs.website.Link]]s
  *
  * @param links
  */
case class Website(links: Iterable[Link]) {

  def toServlet: HttpServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = {
      val path = req.getRequestURI
      if (path startsWith "/404") {
        resp.sendError(404)
      } else {
        val outLinks = links flatMap { _.outlinksFor(path) }
        val webpage = Webpage(path, outLinks)
        resp.setStatus(200)
        resp.setContentType("text/html")
        resp.getWriter.print(webpage.toHtml)
      }
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
