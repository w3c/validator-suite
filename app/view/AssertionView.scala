package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.util.{FutureVal, URL}
import org.w3.vs.model.{AssertorId, Job}

case class AssertionView(
    id: AssertorId,
    name: String,
    warnings: Int,
    errors: Int) extends View
