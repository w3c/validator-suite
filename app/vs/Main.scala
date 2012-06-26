package org.w3.vs


object Main {
  
//  val strategy =
//    Strategy(
//      entrypoint = URL("http://www.bertails.org/"),
//      distance = 0,
//      linkCheck = true,
//      maxNumberOfResources = 100,
//      filter = Filter(include = Everything, exclude = Nothing))
  
//  implicit def configuration = org.w3.vs.Prod.configuration
//      
//  val job = Job.fake(strategy = strategy)
  
  def main(args: Array[String]): Unit = {
    
    val configuration = new DefaultProdConfiguration { }

//    val am = configuration.http.authorityManagerFor(URL("http://www.w3.org/")).sleepTime = 0
//    val run = configuration.runCreator.runOf(RunId(), job)
//    val urls = Await.result(run.URLs(), Duration(3, SECONDS))
//    println("^^^^^^ "+urls.size)

    
  }
  
  
}
