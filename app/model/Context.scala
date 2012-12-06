package org.w3.vs.model

case class Context(
    content: String,
    line: Option[Int], 
    column: Option[Int])
