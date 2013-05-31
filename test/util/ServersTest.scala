package org.w3.vs.util

import org.scalatest.{ Suite, BeforeAndAfterAll }

trait ServersTest extends BeforeAndAfterAll { this: Suite =>

  def servers: Seq[Webserver]

  override def beforeAll(): Unit = {
    super.beforeAll()
    servers foreach { _.start() }
  }

  override def afterAll(): Unit = {
    servers foreach { _.stop() }
    super.afterAll()
  }

}

