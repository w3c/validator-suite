package org.w3.vs.model

import org.w3.util._

import java.util.UUID
import org.joda.time._
import scala.io.Source
import com.codecommit.antixml._

/** A sitemap as in [[http://www.sitemaps.org/ http://www.sitemaps.org/]] */
case class SiteMap(urls: Seq[URLSiteMap])

object SiteMap {
  
  /** parses a sitemap from its XML version */
  def parse(source: Source): SiteMap = {
    val xml = XML.fromSource(source)
    val urls = xml \ "url" map { URLSiteMap.parse(_) }
    SiteMap(urls)
  }

}

case class URLSiteMap(
    loc: URL,
    lastmod: Option[ReadableDateTime])

object URLSiteMap {
  
  def parse(elem: Elem): URLSiteMap = {
    val loc =
      (elem \ "loc" \ text headOption) map { URL(_) } getOrElse sys.error("do something better")
    val lastmod = (elem \ "lastmod" \ text headOption) map { DateTime.parse(_) }
    URLSiteMap(loc, lastmod)
  }
  
}
