package org.w3

import dispatch._
import java.net.{URL => jURL}

package object util {
  
  type Host = String
  type Protocol = String
  type Authority = String
  type Port = Int
  type File = String
  
  type Headers = Map[String, List[String]]
  type ContentType = String
  
  implicit val wrapHeaders = HeadersW.wrapHeaders _
  
  implicit def RequestAsRequestW(req: Request): RequestW = new RequestW(req)
  implicit def jURLAsRequest(url: jURL): Request = new Request(url.toString)
  implicit def jURLAsRequestW(url: jURL): RequestW = new RequestW(jURLAsRequest(url))
  implicit def URLAsRequest(url: URL): Request = new Request(url.toString)
  implicit def URLAsRequestW(url: URL): RequestW = new RequestW(jURLAsRequest(url))
  
}
