package org.w3.util

import java.net.{URL => jURL}
import scalaz.Equal

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
      case e => None
    }

  def externalForm = underlying.toExternalForm

  def openConnection() = underlying.openConnection()
    
}

object URL {

  def apply(url: jURL): URL = URL(url.toString)
  
  implicit def unwrap(url: URL): jURL = url.underlying
  
  def fromString(url: String) = URL(url)
  
  def clearHash(url: URL): URL = URL(new jURL(url.protocol, url.host, url.port, url.file))

  implicit val equal = Equal.equalA[URL]
  
}








