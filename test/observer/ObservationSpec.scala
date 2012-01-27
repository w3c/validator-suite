package org.w3.vs.observer

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.specs2.matcher.BeEqualTo
import java.util.Observable
import org.w3.vs.observer.Observation.{getUrl, getDistance}
import org.specs2.matcher.BeEqualTo
import org.specs2.matcher.BeEqualTo

object ObservationSpec extends Specification {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:8080",
      entrypoint=URL("http://www.w3.org"),
      distance=11,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
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
    val observation =
      Observation(ObserverId(), strategy, toBeExplored = 
        List(
          (w3_home, 0),
          (w3_standards, 1)
        )
      )
    "expose a distance for the known urls" in {
      observation.distanceFor(w3_home) must beEqualTo (Some(0))
      observation.distanceFor(w3_standards) must beEqualTo (Some(1))
    }
    "not know anything about unknown urls" in {
      observation.distanceFor(w3_participate) must beEqualTo (None)
    }
//    "can switch the status for a given url from ToBeExplored to pending" in {
//      val explore = observation.toBeExplored.find(getUrl(_) == w3_home).get
//      explore.status must beEqualTo (ToBeExplored)
//      observation.withPendingFetch(w3_home)
//      explore.status must beEqualTo (Pending)
//    }
//    "be able to forget a url" in {
//      val obs = observation.withoutURLBeingExplored(w3_home)
//      obs.urlsToBeExplored must not contain (Explore(w3_home, 0))
//    }
//    "" in {
//      
//    }
  }
  
  "a fresh observation" should {
    val observation = Observation(ObserverId(), strategy)
    "start in NotYetStartedState" in {
      observation.state must beEqualTo (NotYetStarted)
    }
    "produce an observation in StoppedState when stopped" in {
      val stoppedObs = observation.stop()
      stoppedObs.state must be (StoppedState)
    }
  }
  
  "an observation" should {
    val observation =
      Observation(
        ObserverId(),
        strategy,
        toBeExplored = List(w3_home -> 0, w3_standards -> 1))
    "not have duplicated URLs to be observed" in {
      observation.withNewUrlsToBeExplored(List(w3_home -> 0)) must throwA[AssertionError]
    }
    "should be able to filter URLs to be added" in {
      val newExplores = observation.filteredExtractedURLs(List(w3_home -> 2, w3_standards -> 2, w3_participate -> 2))
      newExplores.size must beEqualTo (1)
      // this should not blow up!
      val obs = observation.withNewUrlsToBeExplored(newExplores)
      obs.toBeExplored.size must beEqualTo (3)
    }
  }
  
  
  "an observation with only w3.org authority" should {
    
    val urls = List(
        w3_home -> 0,
        w3_standards -> 1,
        w3_participate -> 1,
        w3_membership -> 1,
        w3_consortium -> 1)

    val observation = Observation(ObserverId(), strategy, toBeExplored = urls)
    
    "take a URL" in {
      val (ob, nextExplore) = observation.take.get
      nextExplore must beEqualTo (w3_home -> 0)
    }
    
   "not take other urls" in {
      val (obs, nextExplores) = observation.takeAtMost(2)
      nextExplores must beEqualTo (List(w3_home -> 0))
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

    val observation = Observation(ObserverId(), strategy, toBeExplored = urls)
    
    "take a URL from the main authority in priority regardless of the order in the url list" in {
      val (ob, nextExplore) = observation.take.get
      nextExplore must beEqualTo (w3_home -> 0)
    }
    
   "take first a url from the main authority, then the other authorities in the order of appearance in the list" in {
      val (_, nextExplores2) = observation.takeAtMost(2)
      nextExplores2 must contain(w3_home -> 0, mobilevoice -> 1)
      val (_, nextExplores3) = observation.takeAtMost(3)
      nextExplores3 must contain(w3_home -> 0, mobilevoice -> 1, google -> 1)
    }
   
   "not return more urls than what's available" in {
      val (_, nextExplores) = observation.takeAtMost(10)
      nextExplores must contain(w3_home -> 0, mobilevoice -> 1, google -> 1)
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

    val observation = Observation(
        ObserverId(),
        strategy,
        pendingMainAuthority = Some(w3_home -> 0),
        toBeExplored = urls)
    
    "take urls that are not from the main authority" in {
      val (ob, nextExplore) = observation.take.get
      nextExplore must beEqualTo (mobilevoice -> 1)
      val (_, nextExplores) = observation.takeAtMost(10)
      nextExplores must contain(mobilevoice -> 1, google -> 1)
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

    val observation = Observation(
        ObserverId(),
        strategy,
        pending = Map(google -> (google -> 1)),
        toBeExplored = urls)
    
    "take url from the main authority in priority, then urls from other authorities, still ignoring the one with pending authority" in {
      val (ob, nextExplore) = observation.take.get
      nextExplore must beEqualTo (w3_standards -> 1)
      val (_, nextExplores) = observation.takeAtMost(10)
      nextExplores must contain(w3_standards -> 1, mobilevoice -> 2)
    }
    
  }

  "an observation with pending fetches" should {

    val observation = Observation(
        ObserverId(),
        strategy,
        pendingMainAuthority = Some(w3_home -> 0),
        pending = Map(google -> (google -> 1), mobilevoice -> (mobilevoice -> 2)))
    
    "unset the pending state of a url when the response is received" in {
      val obs1 = observation.withNewResponse(w3_home -> ErrorResponse(w3_home, ""))
      obs1.pendingMainAuthority must beEqualTo (None)
      obs1.pending must contain (google -> (google -> 1))
      obs1.pending must contain (mobilevoice -> (mobilevoice -> 2))
      val obs2 = observation.withNewResponse(google -> ErrorResponse(google, ""))
      obs2.pending must not haveKey (google)
      obs2.pendingMainAuthority must beEqualTo (Some(w3_home -> 0))
      obs2.pending must contain (mobilevoice -> (mobilevoice -> 2))
    }
    
    
    
  }


}
