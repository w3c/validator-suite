package org.w3.vs.model

sealed trait Plan {
  // The key used in our urls e.g. /buy?plan=tiny
  def key: String
  // Fastspring product key
  def fastSpringKey: String
}

object Plan {

  // TODO See if fastspring could give us the product key instead of the display name! Unstable.
  def fromFsString(s: String): Option[Plan] = {
    s match {
/*      case "One Time Tiny" => Some(OneTimePlan.Tiny)
      case "One Time Small" => Some(OneTimePlan.Small)
      case "One Time Medium" => Some(OneTimePlan.Medium)
      case "One Time Large" => Some(OneTimePlan.Large)*/
      case "Credits Tiny" => Some(CreditPlan.Tiny)
      case "Credits Small" => Some(CreditPlan.Small)
      case "Credits Medium" => Some(CreditPlan.Medium)
      case "Credits Large" => Some(CreditPlan.Large)
    }
  }

}

sealed trait CreditPlan extends Plan {
  def credits: Int
}

sealed trait OneTimePlan extends Plan {
  def maxPages: Int
}

object CreditPlan {

  case object Tiny extends CreditPlan {
    val credits: Int = 1000
    val fastSpringKey: String = "credits-tiny"
    val key: String = "tiny"
  }

  case object Small extends CreditPlan {
    val credits: Int = 3000
    val fastSpringKey: String = "credits-small"
    val key: String = "small"
  }

  case object Medium extends CreditPlan {
    val credits: Int = 8000
    val fastSpringKey: String = "credits-medium"
    val key: String = "medium"
  }

  case object Large extends CreditPlan {
    val credits: Int = 20000
    val fastSpringKey: String = "credits-large"
    val key: String = "large"
  }

  def fromString(s: String): Option[CreditPlan] = {
    s match {
      case Large.key => Some(Large)
      case Medium.key => Some(Medium)
      case Small.key => Some(Small)
      case Tiny.key => Some(Tiny)
      case _ => None
    }
  }

}

/*object OneTimePlan {

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

}*/
