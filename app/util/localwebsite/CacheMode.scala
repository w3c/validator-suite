package org.w3.vs.util.localwebsite

sealed trait CacheMode
case object Store extends CacheMode
case object AccessFileCache extends CacheMode