package org.w3.vs.observer

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable.Specification
import org.w3.vs.GlobalSystem
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.specs2.matcher.BeEqualTo
import java.util.Observable

object ObservationSpec extends Specification {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:8080",
      entrypoint=URL("http://localhost:8080/"),
      distance=11,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val w3_home = URL("http://www.w3.org")
  val w3_standards = URL("http://www.w3.org/standards")
  val w3_participate = URL("http://www.w3.org/participate")
  val w3_membership = URL("http://www.w3.org/membership")
  val w3_consortium = URL("http://www.w3.org/Consortium")
      
  "an observation with some urls to be explored" should {
    val observation =
      Observation(ObserverId(), strategy, urlsToBeExplored = 
        List(
          Explore(w3_home, 0),
          Explore(w3_standards, 1)
        )
      )
    "expose a distance for the known urls" in {
      observation.distanceFor(w3_home) must beEqualTo (Some(0))
      observation.distanceFor(w3_standards) must beEqualTo (Some(1))
    }
    "not know anything about unknown urls" in {
      observation.distanceFor(w3_participate) must beEqualTo (None)
    }
    "can switch the status for a given url from ToBeExplored to pending" in {
      val explore = observation.urlsToBeExplored.find(_.url == w3_home).get
      explore.status must beEqualTo (ToBeExplored)
      observation.withPendingFetch(w3_home)
      explore.status must beEqualTo (Pending)
    }
    "be able to forget a url" in {
      val obs = observation.withoutURLBeingExplored(w3_home)
      obs.urlsToBeExplored must not contain (Explore(w3_home, 0))
    }
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
        urlsToBeExplored = List(Explore(w3_home, 0), Explore(w3_standards, 1)))
    "not have duplicated URLs to be observed" in {
      observation.withNewUrlsToBeExplored(List(Explore(w3_home, 0))) must throwA[AssertionError]
    }
    "should be able to filter URLs to be added" in {
      val filteredURLs = observation.filteredExtractedURLs(List(w3_home, w3_standards, w3_participate))
      val newExplores = filteredURLs map { Explore(_, 2) }
      newExplores.size must beEqualTo (1)
      // this should not blow up!
      val obs = observation.withNewUrlsToBeExplored(newExplores)
      obs.urlsToBeExplored.size must beEqualTo (3)
    }

  }
  
  
  
  
}
