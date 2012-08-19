package org.w3.vs.view

import org.w3.vs.assertor.Assertor

trait PageFiltering[A <: View] {
  def validate(filter: Option[String]): Option[String]
  def filter(param: Option[String]): (A) => Boolean
}

object PageFiltering {

  implicit val resourcesFiltering: PageFiltering[ResourceView] = new PageFiltering[ResourceView] {

    def filter(param: Option[String]): (ResourceView) => Boolean = _ => true

    def validate(filter: Option[String]): Option[String] = None
  }

  implicit val jobsFiltering: PageFiltering[JobView] = new PageFiltering[JobView] {

    def filter(param: Option[String]): (JobView) => Boolean = _ => true

    def validate(filter: Option[String]): Option[String] = None
  }

  implicit val assertionsFiltering: PageFiltering[AssertionView] = new PageFiltering[AssertionView] {

    def filter(param: Option[String]): (AssertionView) => Boolean = validate(param) match {
      case Some(param) => {
        case assertion if (assertion.assertorName == param) => true
        case _ => false
      }
      case None => _ => true
    }

    def validate(filter: Option[String]): Option[String] = filter match {
      case Some(a) if Assertor.keys.exists(_ == a)  => Some(a)
      case _ => None
    }
  }
}
