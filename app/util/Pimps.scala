package org.w3.util

import scalaz.Validation

object Pimps {
  
  implicit def wrapValidation[E, S](validation: Validation[E, S]): ValidationW[E, S] = new ValidationW(validation)
  
}