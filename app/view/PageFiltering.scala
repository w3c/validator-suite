package org.w3.vs.view

trait PageFiltering[A <: View] {

  def filter(param: Option[String]): (A) => Boolean

}

object PageFiltering {

  implicit val resourcesFiltering: PageFiltering[ResourceView] = new PageFiltering[ResourceView] {
    def filter(param: Option[String]): (ResourceView) => Boolean = _ => true
  }
  implicit val jobsFiltering: PageFiltering[JobView] = new PageFiltering[JobView] {
    def filter(param: Option[String]): (JobView) => Boolean = _ => true
  }
  implicit val assertionsFiltering: PageFiltering[AssertionView] = new PageFiltering[AssertionView] {
    def filter(param: Option[String]): (AssertionView) => Boolean = param match {
      case Some(param) => {
        case assertion if (assertion.assertorName == param) => true
        case _ => false
      }
      case None => _ => true
    }
  }
}
