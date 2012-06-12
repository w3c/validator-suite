package org.w3.util.akkaext

import akka.actor.Actor
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Path

trait PathAwareActor {
this: Actor =>

  def selfPath: URI = new URI(self.path.toString)

  object Child {
    def unapply(path: URI): Option[String] = {
      // There is an unchecked assumption here that path is indeed a subpath of selfPath. 
      val relativized = selfPath.relativize(path).toString
      if (path.toString == relativized) sys.error("you probably wanted to send a message this actor, as the path given to Tell matched the path for this actor. You may want to do the following:\tcase Tell(path, msg) if path == selfPath => ...")
      
      val name = selfPath.relativize(path).toString.split("/")(0)
      if (name.isEmpty) None else Some(name)
    }
  }

}
