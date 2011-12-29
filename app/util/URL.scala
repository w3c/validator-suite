package org.w3.util

import java.net.{URL => jURL}

case class URL(underlying:jURL) {

  override def toString = underlying.toString

  def host: Host = underlying.getHost
  def protocol: Protocol = underlying.getProtocol
  def authority: Authority = underlying.getAuthority
  def port: Port = underlying.getPort
  def file: File = underlying.getFile

  def domain:String = {
    val a = host.split("\\.")
    val s = a.size
    a(s - 2) + "." + a(s - 1)
  }

  def sameDomainAs(other:URL):Boolean = this.domain == other.domain

  def /(spec:String):Option[URL] =
    try {
      val url2 = URL(new jURL(underlying, spec))
      if ("http" == url2.protocol) Some(url2) else None
    } catch {
      case e => None
    }
    
}

object URL {

  def apply(url:String):URL = URL(new jURL(url))
  
  implicit def unwrap(url:URL):jURL = url.underlying
  
  def fromString(url: String) = URL(url)
  
  def clearHash(url: URL): URL = new URL(new jURL(url.protocol, url.host, url.port, url.file))
  
}








