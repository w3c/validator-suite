package org.w3.vs.view

trait Sortable[A] {
  def compare(b: A, sortParam: (String, Boolean)): Boolean
}