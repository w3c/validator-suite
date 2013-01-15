package org.w3.vs.assertor

import java.io._

// defers all operations to $out but counts the written bytes
class CountingOutputStream(out: OutputStream) extends OutputStream {

  var counter: Int = 0

  override def close(): Unit = out.close()

  override def flush(): Unit = out.flush()

  override def write(b: Array[Byte]): Unit = {
    out.write(b)
    counter += b.length
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    out.write(b, off, len)
    counter += len
  }

  def write(b: Int): Unit = {
    out.write(b)
    counter += 1
  }

}
