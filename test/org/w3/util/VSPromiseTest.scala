package org.w3.util

import akka.dispatch._
import akka.util.duration._
import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.junit.JUnitRunner
import java.util.concurrent.{Future => JavaFuture, _}
import play.api.libs.concurrent.{Promise => PlayPromise}
import org.junit.runner.RunWith

// @RunWith(classOf[JUnitRunner])
// class VSPromiseTest extends WordSpec with MustMatchers {
//  
//   val executor: ExecutorService = Executors.newFixedThreadPool(10)
//   implicit val context = ExecutionContext.fromExecutorService(executor)  
//   implicit def timeout(t: java.util.concurrent.TimeoutException): String = "timeout"
//   
//   "a successful VSPromise" should {
//     
//     def promise = VSPromise.successful{Thread.sleep(20); "result"}
//     val f1 = promise
//     
//     "be completed immediatly" in {
//       
//       promise.isCompleted must be (true)
//       f1.isCompleted must be (true)
//     } 
//     
//     "have a success value" in {
//       
//       promise.value1 must be (Some("result"))
//       promise.await(1 millisecond) must be (Some("result"))
//       promise.result(1 millisecond) must be ("result")
//       promise.waitFor(1 millisecond).value1 must be (Some("result"))
//       promise.readyIn(1 millisecond).value1 must be (Some("result"))
//       
//       f1.value1 must be (Some("result"))
//       f1.await(1 millisecond) must be (Some("result"))
//       f1.result(1 millisecond) must be ("result")
//       f1.waitFor(1 millisecond).value1 must be (Some("result"))
//       f1.readyIn(1 millisecond).value1 must be (Some("result"))
//     }
//     
//   }
//   
//   "a VSPromise that does not fail" should {
//     
//     "not be completed initially or completed with a timeout" in {
//       
//       def promise: VSPromise[String] = VSPromise{Thread.sleep(500); "result"}(_ => "throwable").onTimeout("timeout") //.failMap(_ => "timeout")
//       val f1 = promise
//       
//       f1.isCompleted must be (false)
//       f1.value1 must be (None)
//       f1.await(1 millisecond) must be (None)
//       f1.result(1 millisecond) must be ("timeout")
//       f1.waitFor(1 millisecond).value1 must be (None)
//       f1.readyIn(1 millisecond).value1 must be (Some("timeout"))
//       
//       promise.isCompleted must be (false)
//       promise.value1 must be (None)
//       promise.await(1 millisecond) must be (None)
//       promise.result(1 millisecond) must be ("timeout")
//       promise.waitFor(1 millisecond).value1 must be (None)
//       promise.readyIn(1 millisecond).value1 must be (Some("timeout"))
//       
//     }
//     
//     "complete with a success value eventually" in {
//       
//       def promise: VSPromise[String] = VSPromise{Thread.sleep(50); "result"}(_ => "throwable")
//       val f1 = promise
//       
//       Thread.sleep(80)
//       f1.value1 must be (Some("result"))
//       
//       promise.await(10 second) must be (Some("result"))
//       promise.result(10 second) must be ("result")
//       promise.waitFor(10 second).isCompleted must be (true)
//       promise.waitFor(10 second).value1 must be (Some("result"))
//       promise.readyIn(10 second).isCompleted must be (true)
//       promise.readyIn(10 second).value1 must be (Some("result"))
//     }
//     
//   }
//   
//   "a VSPromise that throw an exception" should {
//     
//     val ex: Exception = new Exception("Fake exception") {override def printStackTrace() = println("fake exception for testing")}
//     def promise: VSPromise[Throwable] = VSPromise{throw ex}(t => t)
//     
//     "be completed eventually" in {
// 
//       promise.waitFor(10 millisecond).isCompleted must be (true)
//     }
//     
//     "have a failure value" in {
//       
//       promise.await(10 second) must be (Some(ex))
//       promise.result(10 second) must be (ex)
//       promise.waitFor(10 second).isCompleted must be (true)
//       promise.waitFor(10 second).value1 must be (Some(ex))
//       promise.readyIn(10 second).isCompleted must be (true)
//       promise.readyIn(10 second).value1 must be (Some(ex))
//     }
//   }
//   
//   " - " should {
//     
// //    def promise: VSPromise[String] = VSPromise{Thread.sleep(5000); "result"}(t => "error")
// //    promise
// //        .waitAnd(1 second)(_ => println("1"))
// //        .waitAnd(1 second)(_ => println("2"))
// //        .waitAnd(1 second)(_ => println("3"))
// //        .waitAnd(1 second)(_ => println("4"))
// //        .waitAnd(1 second)(_ => println("5"))
// //        .isCompleted must be (true)
// //    
// //    promise
// //        .waitAnd(1 second)(_ => println("1 -"))
// //        .readyIn(1 second)
// //        .waitAnd(1 second)(_ => println("2")) // must not block
// //        .waitAnd(1 second)(_ => println("3"))
// //        .waitAnd(1 second)(_ => println("4"))
// //        .waitAnd(1 second)(_ => println("5"))
// //        .isCompleted must be (true)
//     
//   }
// 
//   "a VSPromise" should {
//     
//     "be usable in a for loop" in {
//       
//       // flatMap not implemented
//       
// //      def promise = for {
// //        a <- VSPromise.successful("res")
// //        b <- VSPromise.failed(new Exception(a + "ult"))
// //        c <- VSPromise.successful("a")
// //      } yield c
// //      
// //      promise.result(10 millisecond).fold(
// //        f => f.getMessage == "result",
// //        s => false
// //      ) must be (true)
//       
//     }
//     
//     "allow to map on the success value" in {
//       
//       val promise = VSPromise.successful("res")
//       promise.map(r => r + "ult").asInstanceOf[VSPromise[String]].result(10 millisecond) must be ("result")
//     }
//     
//     "allow to map on the failure value" in {
//       
//       val promise = VSPromise.failed("fail")
//       promise.failMap(r => r + "0").result(10 millisecond) must be ("fail0")
//     }
//   }
// }
