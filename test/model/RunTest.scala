package org.w3.vs.model

import org.w3.util._
import org.w3.vs.util._
import org.scalatest._
import org.scalatest.matchers._

/**
 * Server 1 -> Server 2
 * 1 GET       10 HEAD
 */
//class RunTest extends WordSpec with MustMatchers {
//
//  val strategy =
//    Strategy(
//      entrypoint = URL("http://localhost:9001/"),
//      linkCheck = true,
//      maxResources = 100,
//      filter = Filter(include = Everything, exclude = Nothing),
//      assertorsConfiguration = Map.empty)
//
//  "A fresh run" must {
//
//    val fresh = Run.freshRun(UserId(), JobId(), strategy)
//
//    "not be Running" in {
//      
//      fresh.activity must be (Idle)
//
//    }
//
//  }
//
//}

