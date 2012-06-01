package org.w3.vs.run

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.scalatest.{Filter => _, _}
import org.scalatest.matchers.MustMatchers
import org.joda.time.DateTime

abstract class ObservationSpec extends WordSpec with MustMatchers {

  implicit def configuration = org.w3.vs.Prod.configuration
  
  println("11111")
  
  val strategy =
    Strategy(
      entrypoint=URL("http://www.w3.org"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val w3 = Job(
      createdOn = DateTime.now,
      name = "W3C",
      creatorId = UserId(),
      organizationId = OrganizationId(),
      strategy = strategy)
      
  val w3_home = URL("http://www.w3.org")
  val w3_standards = URL("http://www.w3.org/standards")
  val w3_participate = URL("http://www.w3.org/participate")
  val w3_membership = URL("http://www.w3.org/membership")
  val w3_consortium = URL("http://www.w3.org/Consortium")
  val mobilevoice = URL("http://mobilevoiceconference.com/")
  val google = URL("http://www.google.com/")
  val googlefoo = URL("http://www.google.com/foo")
  val googlebar = URL("http://www.google.com/bar")
      
  "an observation with some urls to be explored" should {
    val (data, _) = Run(w3).withNewUrlsToBeExplored(List(w3_home -> 0, w3_standards -> 1))
    "expose a distance for the known urls" in {
      data.distance.get(w3_home) must equal (Some(0))
      data.distance.get(w3_standards) must equal (Some(1))
    }
    "not know anything about unknown urls" in {
      data.distance.get(w3_participate) must equal (None)
    }
  }
  
  "an observation" should {
    val (initialData, _) = Run(w3).withNewUrlsToBeExplored(List(w3_home -> 0, w3_standards -> 1))
    "should be able to filter URLs to be added" in {
      val (data, newUrls) = initialData.withNewUrlsToBeExplored(List(w3_home, w3_standards, w3_participate), 2)
      newUrls must equal (List(w3_participate))
    }
  }
  
  "an observation with only w3.org authority" should {
    
    val urls = List(
        w3_home -> 0,
        w3_standards -> 1,
        w3_participate -> 1,
        w3_membership -> 1,
        w3_consortium -> 1)

    val (initialData, _) = Run(w3).withNewUrlsToBeExplored(urls)
    
    "take a URL" in {
      val (data, url) = initialData.take.get
      url must equal (w3_home)
    }
    
   "not take other urls" in {
      val (data, nextUrls) = initialData.takeAtMost(2)
      nextUrls must equal (List(w3_home -> 0))
    }
   
  }
  
  
  "an observation with w3.org authority and other 2 other authorities" should {
    
    val urls = List(
        mobilevoice -> 1,
        w3_home -> 0,
        w3_standards -> 1,
        w3_participate -> 1,
        w3_membership -> 1,
        w3_consortium -> 1,
        google -> 1)

    val (initialData, _) = Run(w3).withNewUrlsToBeExplored(urls)
    
    "take a URL from the main authority in priority regardless of the order in the url list" in {
      val (data, nextUrl) = initialData.take.get
      nextUrl must equal (w3_home)
    }
    
   "take first a url from the main authority, then the other authorities in the order of appearance in the list" in {
      val (data, nextUrls) = initialData.takeAtMost(2)
      nextUrls must equal (List(w3_home -> 0, mobilevoice -> 1))
      val (data2, nextUrls2) = initialData.takeAtMost(3)
      nextUrls2 must equal (List(w3_home -> 0, mobilevoice -> 1, google -> 1))
    }
   
   "not return more urls than what's available" in {
      val (_, nextUrls) = initialData.takeAtMost(10)
      nextUrls must equal (List(w3_home -> 0, mobilevoice -> 1, google -> 1))
    }

  }
  

  
  "an observation with 1. pending fetch for main authority 2. other w3.org urls to be fetched 3. other non-w3.org urls to be fetched" should {
    
    val urls = List(
        w3_standards -> 1,
        w3_participate -> 1,
        mobilevoice -> 1,
        w3_membership -> 1,
        w3_consortium -> 1,
        google -> 1)

    val (initialData, _) =
      Run(job = w3, distance = Map(w3_home -> 0), pending = Set(w3_home), data = JobData(jobId = w3.id))
      .withNewUrlsToBeExplored(urls)
    
    "take urls that are not from the main authority" in {
      val (data, nextUrl) = initialData.take.get
      nextUrl must equal (mobilevoice)
      val (_, nextUrls) = initialData.takeAtMost(10)
      nextUrls must equal (List(mobilevoice -> 1, google -> 1))
    }
    
  }

  "an observation with 1. pending fetch for non-main authority 2. other w3.org urls to be fetched 3. other non-w3.org urls to be fetched" should {
    
    val urls = List(
        googlebar -> 1,
        w3_standards -> 1,
        w3_participate -> 1,
        googlefoo -> 1,
        w3_membership -> 1,
        mobilevoice -> 2,
        w3_consortium -> 1)
    
    val (initialData, _) =
      Run(job = w3, distance = Map(google -> 1), pending = Set(google), data = JobData(jobId = w3.id))
      .withNewUrlsToBeExplored(urls)
    
    "take url from the main authority in priority, then urls from other authorities, still ignoring the one with pending authority" in {
      val (data, nextUrl) = initialData.take.get
      nextUrl must equal (w3_standards)
      val (_, nextUrls) = initialData.takeAtMost(10)
      nextUrls must equal (List(w3_standards -> 1, mobilevoice -> 2))
    }
    
  }

  "an observation with pending fetches" should {

    val initialData =
      Run(job = w3, distance = Map(w3_home -> 0, google -> 1, mobilevoice -> 2),
        pending = Set(w3_home, google, mobilevoice), data = JobData(jobId = w3.id))
    
    "unset the pending state of a url when the response is received" in {
      val data = initialData.withResourceResponse(new HttpResponse(
          jobId = JobId(),
          runId = RunId(),
          url = w3_home,
          action = GET,
          status = 200,
          headers = Map.empty,
          extractedURLs = List.empty))
      data.pending must equal (Set(google, mobilevoice))
      data.fetched must equal (Set(w3_home))
      
      val data2 = initialData.withResourceResponse(new HttpResponse(
          jobId = JobId(),
          runId = RunId(),
          url = google,
          action = GET,
          status = 200,
          headers = Map.empty,
          extractedURLs = List.empty))
      data2.pending must equal (Set(w3_home, mobilevoice))
      data2.fetched must equal (Set(google))
    }
    
    
    
  }


}
