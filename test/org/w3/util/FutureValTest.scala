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

@RunWith(classOf[JUnitRunner])
class FutureValTest extends WordSpec with MustMatchers {
 
  val executor: ExecutorService = Executors.newFixedThreadPool(10)
  implicit val context = ExecutionContext.fromExecutorService(executor)  
  
  "a successful FutureVal" should {
    
    def future = FutureVal.successful{Thread.sleep(20); "result"}
    val f1 = future
    
    "be completed immediatly" in {
      
      future.isCompleted must be (true)
      f1.isCompleted must be (true)
    } 
    
    "have a success value" in {
      
      future.value must be (Some(Success("result")))
      future.await(1 millisecond) must be (Some(Success("result")))
      future.result(1 millisecond) must be (Success("result"))
      future.waitFor(1 millisecond).value must be (Some(Success("result")))
      future.readyIn(1 millisecond).value must be (Some(Success("result")))
      
      f1.value must be (Some(Success("result")))
      f1.await(1 millisecond) must be (Some(Success("result")))
      f1.result(1 millisecond) must be (Success("result"))
      f1.waitFor(1 millisecond).value must be (Some(Success("result")))
      f1.readyIn(1 millisecond).value must be (Some(Success("result")))
    }
    
  }
  
  "a FutureVal that does not fail" should {
    
    "not be completed initially or completed with a timeout" in {
      
      def future: FutureVal[String, String] = FutureVal{Thread.sleep(500); "result"}.failMap(_ => "timeout")
      val f1 = future
      
      f1.isCompleted must be (false)
      f1.value must be (None)
      f1.await(1 millisecond) must be (None)
      f1.result(1 millisecond) must be (Failure("timeout"))
      f1.waitFor(1 millisecond).value must be (None)
      f1.readyIn(1 millisecond).value must be (Some(Failure("timeout")))
      
      future.isCompleted must be (false)
      future.value must be (None)
      future.await(1 millisecond) must be (None)
      future.result(1 millisecond) must be (Failure("timeout"))
      future.waitFor(1 millisecond).value must be (None)
      future.readyIn(1 millisecond).value must be (Some(Failure("timeout")))
      
    }
    
    "complete with a success value eventually" in {
      
      def future: FutureVal[String, String] = FutureVal{Thread.sleep(50); "result"}.failMap(_ => "timeout")
      val f1 = future
      
      Thread.sleep(80)
      f1.value must be (Some(Success("result")))
      
      future.await(10 second) must be (Some(Success("result")))
      future.result(10 second) must be (Success("result"))
      future.waitFor(10 second).isCompleted must be (true)
      future.waitFor(10 second).value must be (Some(Success("result")))
      future.readyIn(10 second).isCompleted must be (true)
      future.readyIn(10 second).value must be (Some(Success("result")))
    }
    
  }
  
  "a FutureVal that throw an exception" should {
    
    val ex: Exception = new Exception("Fake exception") {override def printStackTrace() = println("fake exception for testing")}
    def future: FutureVal[Throwable, Unit] = FutureVal{throw ex; ""}
    
    "be completed eventually" in {

      future.waitFor(10 millisecond).isCompleted must be (true)
    }
    
    "have a failure value" in {
      
      future.await(10 second) must be (Some(Failure(ex)))
      future.result(10 second) must be (Failure(ex))
      future.waitFor(10 second).isCompleted must be (true)
      future.waitFor(10 second).value must be (Some(Failure(ex)))
      future.readyIn(10 second).isCompleted must be (true)
      future.readyIn(10 second).value must be (Some(Failure(ex)))
    }
  }
  
  " - " should {
    
//    def future: FutureVal[Throwable, String] = FutureVal{Thread.sleep(5000); "result"}
//    future
//        .waitFor(1 second, println("1"))
//        .waitFor(1 second, println("2"))
//        .waitFor(1 second, println("3"))
//        .waitFor(1 second, println("4"))
//        .waitFor(1 second, println("5"))
//        .isCompleted must be (true)
//    
//    future
//        .waitFor(1 second, println("1 -"))
//        .readyIn(1 second)
//        .waitFor(1 second, println("2")) // must not block
//        .waitFor(1 second, println("3"))
//        .waitFor(1 second, println("4"))
//        .waitFor(1 second, println("5"))
//        .isCompleted must be (true)
    
  }

  "a FutureVal" should {
    
    "be usable in a for loop" in {
      
      def future = for {
        a <- FutureVal.successful("res")
        b <- FutureVal.failed(new Exception(a + "ult"))
        c <- FutureVal.successful("a")
      } yield c
      
      future.result(10 millisecond).fold(
        f => f.getMessage == "result",
        s => false
      ) must be (true)
      
    }
    
    "allow to map on the success value" in {
      
      val future = FutureVal.successful("res")
      future.map(r => r + "ult").result(10 millisecond) must be (Success("result"))
    }
    
    "allow to map on the failure value" in {
      
      implicit def onTo(t: TimeoutException): String = ""
      val future = FutureVal.failed("fail")
      future.failMap(r => r + "0").result(10 millisecond) must be (Failure("fail0"))
    }
  }
}