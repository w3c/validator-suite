/*package org.w3.util

import akka.dispatch._
import akka.util.duration._
import akka.util.Duration
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.scalatest.matchers.{ Matcher, MatchResult }
import scalaz._
import play.api.libs.concurrent.{Promise => PlayPromise, _}
import java.util.concurrent.{Future => JavaFuture, _}
import java.util.concurrent.{ExecutorService, Executors}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

/* Re: output warning, cf https://github.com/playframework/Play20/wiki/AkkaCore */

@RunWith(classOf[JUnitRunner])
class PromiseTest extends WordSpec with MustMatchers {
  
  val executor: ExecutorService = Executors.newFixedThreadPool(10)
  implicit val context = ExecutionContext.fromExecutorService(executor)
  
  "A Promise" should {
    
    "> STMPromise" in {
      def p() = {
        val promise = PlayPromise[String]()
        Future{Thread.sleep(10); promise.redeem("result")}
        promise
      }
      //p().map(ad => ad).await(50) must be (Redeemed("result"))
      
      p().await(0).isInstanceOf[Thrown] must be (true)
      p().await(50) must be (Redeemed("result"))
      p().map(ad => ad).await(500) must be (Redeemed("result"))
      //p().map(a => a).await(0).isInstanceOf[Thrown] must be (true)
      // Fails! How much time does a trivial map takes ?
      //p().map(ad => ad + "0").await(0).isInstanceOf[Thrown] must be (true)
      p().map(ad => ad).await(50) must be (Redeemed("result"))
      p().map(ad => ad + "0").await(50) must be (Redeemed("result0"))
      
    }
    
    "> some weird maps map" in {
      
      // Akka promise
      val a = Promise.successful("a")
      a.isCompleted must be (true)
      a.value.isDefined must be (true)
      //a.map(a => a).isCompleted must be (true) // wtf? fails
      
      val b = PlayPromise.pure("b")
      b.map(b => b).await(0) must be (Redeemed("b"))
    }
    
    "> value.isDefined always returns true, the promise is never the object Waiting" in {
      
      val a = Future{Thread.sleep(50); "result"}.asPromise
       
      // The promise should be waiting at that point (right?) but next test fails
      //a must be (Waiting)
      
      // * a.value is blocking and equivalent to a.await(5000)! Not obvious
      // * no way to probe for the current value other than with await(0)
      // * isDefined always return true. Related to the promise never being Waiting. Instead the value of the promise is a Thrown[Timeout]
      //a.await(0).isDefined must be (false) 
      a.await(0).isInstanceOf[Thrown] must be (true)
      a.await(30).isInstanceOf[Thrown] must be (true)
      a.await(70) must be (Redeemed("result"))
    }
    
    "> or() and orTimeout() fail" in {
      
      // First test by creating sleeping threads
      def sleepingPromise(duration: Long = 100, result: String = "result") = Future{Thread.sleep(duration); result}.asPromise
      
      def a() = sleepingPromise(100, "a")
      def b() = sleepingPromise(200, "b")
      
      sleepingPromise(100, "a").await(0).isInstanceOf[Thrown] must be (true)
      b().await(0).isInstanceOf[Thrown] must be (true)
      
      // bug
      // a.or(b).await can throw a TimeoutException (instead of being a Thrown(_)) if the 
      // timeout is lower that the shortest execution time!
      // cf patch of STMPromise
      a().or(b()).await(50).isInstanceOf[Thrown] must be (true)
      b().or(a()).await(50).isInstanceOf[Thrown] must be (true)
      a().or(b()).await(150) must be (Redeemed(Left("a")))
      b().or(a()).await(150) must be (Redeemed(Right("a")))
      a().or(b()).await(250) must be (Redeemed(Left("a")))
      b().or(a()).await(250) must be (Redeemed(Right("a")))
      
      // ----------------
      
      // Second test by creating scheduled promises as Play does it with onTimeout
      def scheduledRedeem(duration: akka.util.Duration, result: String) = {
        val p = new STMPromise[String]()
        play.core.Invoker.system.scheduler.scheduleOnce(duration)(p.redeem(result))
        p
      }
      def a1() = scheduledRedeem(100 millisecond, "a")
      def b1() = scheduledRedeem(200 millisecond, "b")
      
      a1().or(b1()).await(50).isInstanceOf[Thrown] must be (true)
      b1().or(a1()).await(50).isInstanceOf[Thrown] must be (true)
      // Next 2 fail
      //a1().or(b1()).await(150) must be (Redeemed(Left("a")))
      //b1().or(a1()).await(150) must be (Redeemed(Right("a")))
      a1().or(b1()).await(250) must be (Redeemed(Left("a")))
      b1().or(a1()).await(250) must be (Redeemed(Right("a")))
      
      // ----------------
      
      // Using onTimeout
      a().orTimeout("timeout", 200).await(50).isInstanceOf[Thrown] must be (true)
      b().orTimeout("timeout", 100).await(50).isInstanceOf[Thrown] must be (true)
      a().orTimeout("timeout", 200).await(150) must be (Redeemed(Left("a")))
      // Fails!
      //b().orTimeout("timeout", 100).await(150) must be (Redeemed(Right("timeout")))
      a().orTimeout("timeout", 200).await(250) must be (Redeemed(Left("a")))
      // We have to wait for the end of the first thread to finally get the timeout value!!
      b().orTimeout("timeout", 100).await(250) must be (Redeemed(Right("timeout")))
      
    }
  }
  
}*/