package org.w3.vs.util.website

/** a pimp for String to build [[org.w3.vs.website.Link]]s */
class StringW(s: String) {
  
  /** lets you write things like
    * {{{ 
    * "/index.html" --> ("/1.html", "/2.html", "/3.html")
    * }}}
    */
  def -->(urls: String*): Link = {
    /* here is a trick with the partial function that is built
     * the list of URLs is static, hence the partial function that reacts on anything
     * also, the matched URL should be a regex that will behave like String equality, hence the enclosing parenthesis
     */
    Link("("+s+")", { case _ => urls.toSeq })
  }
  
  /** lets you write things like
  * {{{ 
  * """/(\d)/""" ---> { case i => (1 to 50) map { j => "/%s/%d" format (i, j) } }
  * }}}
  * In this examples, any URL matching '/(\d)/' will be considered
  * The matching String coming from the group '(\d)' is made available to the partial function
  * and can be used to generate the outgoing links to this page.
  * The whole process is computed at runtime.  
  */
  def --->(pf: PartialFunction[Any, Seq[String]]): Link = Link(s, pf)
}

object StringW {
  implicit def wrapString(s:String):StringW = new StringW(s)
}
