package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._
import java.io.{File, FileWriter}
import com.mongodb.util.JSON
import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.casbah.Imports._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

class MemoryStore extends Store {
  
  import java.util.concurrent.ConcurrentHashMap
  import scala.collection.JavaConversions.{JConcurrentMapWrapper => JCMWrapper}
  private val states = JCMWrapper(new ConcurrentHashMap[ObserverId, ObserverState])
  
  def init(): Either[Throwable, Unit] = Right()
  
  def list: Either[Throwable, Traversable[ObserverState]] = Right(states.values)
  
  def get(id: ObserverId): Either[Throwable, ObserverState] =
    try {
      Right(states(id))
    } catch { case t: Throwable =>
      Left(t)
    }
  
  
  def save(state: ObserverState): Either[Throwable, Unit] = {
    states += state.id -> state
    Right()
  }
  
}
