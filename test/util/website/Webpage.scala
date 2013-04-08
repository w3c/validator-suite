package org.w3.vs.util.website

/** A webpage that is just a title and a sequence of links
  * 
  * @param title
  * @param outLinks
  */
case class Webpage(title:String, links:Iterable[String]) {
  /** renders this webpage as HTML */
  def toHtml:String = Webpage.template format (title, Link.toUL(links))
}

object Webpage {
  
  val template =
"""<!Doctype html>
<html>
  <head>
    <title>%s</title>
  </head>
  <body>
%s
  </body>
</html>
"""
  
}