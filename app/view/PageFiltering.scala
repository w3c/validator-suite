package org.w3.vs.view

import org.w3.vs.assertor.Assertor
import org.w3.vs.view.model.{ResourceView, JobView, AssertionView}

trait PageFiltering[A <: View] {
  def validate(filter: Option[String]): Option[String]
  def filter(param: Option[String]): (A) => Boolean
}

object PageFiltering {





}
