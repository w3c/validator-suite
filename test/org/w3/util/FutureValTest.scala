package org.w3.util

import akka.dispatch._
import akka.util.duration._
import akka.util.Duration
import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.{Future => JavaFuture, _}
import play.api.libs.concurrent.{Promise => PlayPromise, _}
import org.junit.runner.RunWith
import scalaz._

/* Re: output warning, cf https://github.com/playframework/Play20/wiki/AkkaCore */

//trait CustomMatchers {
//
//  class OddMatcher extends BeMatcher[Int] {
//    def apply(left: Validation[TimeoutException, String]) =
//      MatchResult(
//        left % 2 == 1,
//        left.toString + " was even",
//        left.toString + " was odd"
//      )
//  }
//  
//  val timeoutFailure = new OddMatcher
//  
//}

@RunWith(classOf[JUnitRunner])
class FutureValTest extends WordSpec with MustMatchers {
 
  val executor: ExecutorService = Executors.newFixedThreadPool(10)
  implicit val context = ExecutionContext.fromExecutorService(executor)  
  
  "a successful FutureVal" should {
    
    implicit def timeout(t: Throwable): String = "timeout"
    def future: FutureVal[String, String] = FutureVal.successful{Thread.sleep(20); "result"}
    val f1 = future
    
    "be completed immediatly" in {
      
      future.isCompleted must be (true)
      f1.isCompleted must be (true)
    } 
    
    "have a success value" in {
      
      future.value must be (Some(Success("result")))
      future.await(1 millisecond) must be (Some(Success("result")))
      future.result(1 millisecond) must be (Success("result"))
      future.waitAnd(1 millisecond).value must be (Some(Success("result")))
      future.readyIn(1 millisecond).value must be (Some(Success("result")))
      
      f1.value must be (Some(Success("result")))
      f1.await(1 millisecond) must be (Some(Success("result")))
      f1.result(1 millisecond) must be (Success("result"))
      f1.waitAnd(1 millisecond).value must be (Some(Success("result")))
      f1.readyIn(1 millisecond).value must be (Some(Success("result")))
    }
    
  }
  
  "a FutureVal that does not fail" should {
    
    "not be completed initially or completed with a timeout" in {
      
      implicit def timeout(t: Throwable): String = "timeout"
      def future: FutureVal[String, String] = FutureVal{Thread.sleep(500); "result"}
      val f1 = future
      
      f1.isCompleted must be (false)
      f1.value must be (None)
      f1.await(1 millisecond) must be (None)
      f1.result(1 millisecond) must be (Failure("timeout"))
      f1.waitAnd(1 millisecond).value must be (None)
      f1.readyIn(1 millisecond).value must be (Some(Failure("timeout")))
      
      future.isCompleted must be (false)
      future.value must be (None)
      future.await(1 millisecond) must be (None)
      future.result(1 millisecond) must be (Failure("timeout"))
      future.waitAnd(1 millisecond).value must be (None)
      future.readyIn(1 millisecond).value must be (Some(Failure("timeout")))
      
    }
    
    "complete with a success value eventually" in {
      
      implicit def timeout(t: Throwable): String = "timeout"
      def future: FutureVal[String, String] = FutureVal{Thread.sleep(50); "result"}
      val f1 = future
      
      Thread.sleep(80)
      f1.value must be (Some(Success("result")))
      
      future.await(10 second) must be (Some(Success("result")))
      future.result(10 second) must be (Success("result"))
      future.waitAnd(10 second).isCompleted must be (true)
      future.waitAnd(10 second).value must be (Some(Success("result")))
      future.readyIn(10 second).isCompleted must be (true)
      future.readyIn(10 second).value must be (Some(Success("result")))
    }
    
  }
  
  "a FutureVal that throw an exception" should {
    
    val ex: Exception = new Exception("Fake exception") {override def printStackTrace() = println("fake exception for testing")}
    def future: FutureVal[Throwable, Unit] = FutureVal{throw ex; ""}
    
    "be completed eventually" in {

      future.waitAnd(10 millisecond).isCompleted must be (true)
    }
    
    "have a failure value" in {
      
      future.await(10 second) must be (Some(Failure(ex)))
      future.result(10 second) must be (Failure(ex))
      future.waitAnd(10 second).isCompleted must be (true)
      future.waitAnd(10 second).value must be (Some(Failure(ex)))
      future.readyIn(10 second).isCompleted must be (true)
      future.readyIn(10 second).value must be (Some(Failure(ex)))
    }
  }
  
  " - " should {
    
//    def future: FutureVal[Throwable, String] = FutureVal{Thread.sleep(5000); "result"}
//    future
//        .waitAnd(1 second, println("1"))
//        .waitAnd(1 second, println("2"))
//        .waitAnd(1 second, println("3"))
//        .waitAnd(1 second, println("4"))
//        .waitAnd(1 second, println("5"))
//        .isCompleted must be (true)
//    
//    future
//        .waitAnd(1 second, println("1 -"))
//        .readyIn(1 second)
//        .waitAnd(1 second, println("2")) // must not block
//        .waitAnd(1 second, println("3"))
//        .waitAnd(1 second, println("4"))
//        .waitAnd(1 second, println("5"))
//        .isCompleted must be (true)
    
  }

  "a FutureVal" should {
    
    "be usable in a for loop" in {
      
      val future = for {
        a <- FutureVal.successful[Throwable, String]("res")
        b <- FutureVal.failed(new Exception(a + "ult"))
      } yield b
      
      future.await(10 millisecond)
      
      future.value.get.fold(
        f => f.getMessage == "result",
        s => false
      ) must be (true)
    }
    
    "allow to map on the success value" in {
      
      val future = FutureVal.successful[Throwable, String]("res")
      future.map(r => r + "ult").result(10 millisecond) must be (Success("result"))
    }
    
    "allow to map on the failure value" in {
      
      implicit def onThrowable(t: Throwable): String = ""
      val future = FutureVal.failed[String, String]("fail")
      future.failMap(r => r + "0").result(10 millisecond) must be (Failure("fail0"))
    }
  }
}