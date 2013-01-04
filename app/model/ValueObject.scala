package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs._

case class UserVO(
  name: String,
  email: String,
  password: String,
  isSubscriber: Boolean)
