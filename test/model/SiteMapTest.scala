/*package org.w3.vs.model

import org.w3.vs.util._
import org.specs2.mutable._

import scala.io.Source
import org.joda.time._

object SiteMapTest extends Specification {

  "parsing src/test/resources/sitemap.xml should produce the expected sitemap" in {
    
    val source = Source.fromFile("test/resources/sitemap.xml")
    
    val sitemap = SiteMap.parse(source)
    
    val expectedSitemap =
      SiteMap(
      Seq(URLSiteMap(URL("http://www.example.com/"), Some(new DateMidnight(2005,1,1))),
          URLSiteMap(URL("http://www.example.com/catalog?item=12&desc=vacation_hawaii"), None),
          URLSiteMap(URL("http://www.example.com/catalog?item=73&desc=vacation_new_zealand"), Some(new DateMidnight(2004,12,23))),
          URLSiteMap(URL("http://www.example.com/catalog?item=74&desc=vacation_newfoundland"),Some(new DateTime(2004,12,23,18,00,15,DateTimeZone.UTC))),
          URLSiteMap(URL("http://www.example.com/catalog?item=83&desc=vacation_usa"),Some(new DateMidnight(2004,11,23)))))
          
    expectedSitemap should beEqualTo(sitemap)
    
  }


}*/