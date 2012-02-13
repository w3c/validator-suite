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

object FileStore {
  
  def deleteDirectory(path: Path): Unit = {
    val visitor = new SimpleFileVisitor[Path] {
      override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(path)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: Path, e: java.io.IOException): FileVisitResult = {
        if (e == null) {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        } else {
          // directory iteration failed
          throw e
        }
      }
    }
    Files.walkFileTree(path, visitor)
  }
  
}

class FileStore(base: File) extends Store {
  
  assert(base.isDirectory, "%s must be an existing directory" format base.getAbsolutePath)
  
  def init(): Either[Throwable, Unit] = {
    FileStore.deleteDirectory(base.toPath)
    Files.createDirectory(base.toPath)
    Right(())
  }
  
  def list: Either[Throwable, Traversable[ObserverState]] =
   try {
     val states = base.list map { filename => get(ObserverId(filename)).right.get }
     Right(states.toTraversable)
   } catch { case t: Throwable =>
     Left(t)
   }
  
  def get(id: ObserverId): Either[Throwable, ObserverState] =
    try {
      val stateFile = new File(base, id.toString)
      val source = io.Source.fromFile(stateFile)
      val json = source.getLines.mkString("\n")
      val dbobject = JSON.parse(json).asInstanceOf[DBObject]
      val observerState = grater[ObserverState].asObject(dbobject)
      Right(observerState)
    } catch { case t: Throwable =>
      Left(t)
    }
  
  
  def save(state: ObserverState): Either[Throwable, Unit] =
    try {
      val stateFile = new File(base, state.id.toString)
      val dbo = grater[ObserverState].asDBObject(state)
      val json = dbo.toString
      val writer = new FileWriter(stateFile)
      writer.write(json)
      writer.close()
      Right(())
    } catch { case t: Throwable =>
      Left(t)
    }
  
}
