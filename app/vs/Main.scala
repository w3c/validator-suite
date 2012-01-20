package org.w3.vs

import org.w3.util._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS

object Main {
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="w3.org",
      entrypoint=URL("http://www.bertails.org/"),
      distance=0,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))

  def main(args: Array[String]): Unit = {
    
    GlobalSystem.init()

    val am = GlobalSystem.http.authorityManagerFor(URL("http://www.w3.org/")).sleepTime = 0
    val observer = GlobalSystem.observerCreator.observerOf(ObserverId(), strategy, timeout = Duration(10, SECONDS))
    observer.startExplorationPhase()
    val urls = Await.result(observer.URLs(), Duration(10, SECONDS))
    println("^^^^^^ "+urls.size)

    
  }
  
  
}