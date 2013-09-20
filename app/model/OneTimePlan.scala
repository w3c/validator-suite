package org.w3.vs.model

sealed trait OneTimePlan {
  // The key used in our urls e.g. /buy?plan=tiny
  def key: String
  // Fastspring product key
  def fastSpringKey: String
  // max pages for this plan
  def maxPages: Int
}

object OneTimePlan {

  /*def fromOpt(o: Option[String]): OneTimePlan = {
    o match {
      case Some("tiny") => Tiny
      case Some("small") => Small
      case Some("medium") => Medium
      case _ => Large
    }
  }*/

  // Closest OneTimePlan for a given job (necessary while the model doesn't capture that information)
  def fromJob(job: Job): OneTimePlan = {
    job.strategy.maxResources match {
      case n if n <= Tiny.maxPages => Tiny
      case n if n <= Small.maxPages => Small
      case n if n <= Medium.maxPages => Medium
      case n if n <= Large.maxPages => Large
      case _ => throw new Exception("this job max pages exceed the maximum one-time job value")
    }
  }

  def fromString(s: String): Option[OneTimePlan] = {
    s match {
      case Large.key => Some(Large)
      case Medium.key => Some(Medium)
      case Small.key => Some(Small)
      case Tiny.key => Some(Tiny)
      case _ => None
    }
  }

  case object Tiny extends OneTimePlan {
    val key = "tiny"
    val fastSpringKey = "one-time-tiny"
    val maxPages = 250
  }

  case object Small extends OneTimePlan {
    val key = "small"
    val fastSpringKey = "one-time-small"
    val maxPages = 500
  }

  case object Medium extends OneTimePlan{
    val key = "medium"
    val fastSpringKey = "one-time-medium"
    val maxPages = 1000
  }

  case object Large extends OneTimePlan{
    val key = "large"
    val fastSpringKey = "one-time-large"
    val maxPages = 2000
  }

}
