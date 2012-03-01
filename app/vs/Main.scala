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
  
  val job = Job(strategy = strategy)
  
  def main(args: Array[String]): Unit = {
    
    val configuration = new prod.Configuration { }

//    val am = configuration.http.authorityManagerFor(URL("http://www.w3.org/")).sleepTime = 0
//    val run = configuration.runCreator.runOf(RunId(), job)
//    val urls = Await.result(run.URLs(), Duration(10, SECONDS))
//    println("^^^^^^ "+urls.size)

    
  }
  
  
}