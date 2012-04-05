package org.w3.util.akkaext

import akka.actor._
import java.nio.file._

object PathAware {

  def apply(root: ActorRef, path: ActorPath): PathAware = new PathAware(root, path)

}

class PathAware(root: ActorRef, path: ActorPath) {

  val jpath = Paths.get(path.toString)

  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    root ! Tell(jpath, message)

  import akka.pattern.ask

}
