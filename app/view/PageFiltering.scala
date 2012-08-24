package org.w3.vs.view

trait PageFiltering[A <: View] {
  def validate(filter: Option[String]): Option[String]
  def filter(filter: Option[String]): (A) => Boolean
  def search(search: Option[String]): (A) => Boolean
}
