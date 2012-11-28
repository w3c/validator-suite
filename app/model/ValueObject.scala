package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs._

sealed trait ValueObject

case class JobVO (
    name: String,
    createdOn: DateTime,
    strategy: Strategy,
    creator: UserId) extends ValueObject

case class UserVO(
    name: String,
    email: String,
    password: String) extends ValueObject
