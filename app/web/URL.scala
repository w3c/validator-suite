package org.w3.vs.web

import java.net.{ URL => jURL, URLEncoder, URI }
import scalaz.Equal
import scala.util.Try
import org.w3.vs.util._

case class URL(url: String) {
  
  val underlying: jURL = new jURL(url)

  override def toString = underlying.toString

  def host: Host = underlying.getHost
  def protocol: Protocol = underlying.getProtocol
  def authority: Authority = underlying.getAuthority
  def port: Port = underlying.getPort
  def file: FileName = underlying.getFile

  def domain: String = {
    val a = host.split("\\.")
    val s = a.size
    a(s - 2) + "." + a(s - 1)
  }

  def sameDomainAs(other: URL): Boolean = this.domain == other.domain

  def /(spec: String): Option[URL] =
    try {
      val url2 = URL(new jURL(underlying, spec))
      if ("http" == url2.protocol || "https" == url2.protocol) Some(url2) else None
    } catch {
      case e: Exception => None
    }

  def encode(enc: String): String = URLEncoder.encode(url, enc)

  /** does the correct convertion to a URI as per RFC 2396
    */
  def toURI: URI =
    new URI(underlying.getProtocol(), underlying.getUserInfo(), underlying.getHost(), underlying.getPort(), underlying.getPath(), underlying.getQuery(), underlying.getRef())

  /** returns an HTTP Client friendly URL as being defined by RFC 2396
    * DO NOT USE ANY OTHER METHODS as java.net.URL does not do that properly
    */
  def httpClientFriendly: String = toURI.toString

  def openConnection() = underlying.openConnection()

  def shorten(limit: Int): String =
    org.w3.vs.view.Helper.shorten(url.replaceFirst("http://", ""), limit)
    
}

object URL {

  implicit val ordering: Ordering[URL] = new Ordering[URL] {
    def compare(x: URL, y: URL): Int = 
       scala.math.Ordering.String.compare(x.url, y.url)
  }

  def apply(url: jURL): URL = URL(url.toString)
  
  implicit def unwrap(url: URL): jURL = url.underlying
  
  def fromString(url: String) = URL(url)
  
  def clearHash(url: URL): URL = URL(new jURL(url.protocol, url.host, url.port, url.file))

  implicit val equal = Equal.equalA[URL]
  
}








