package com.swissborg

package object lithium {

  @specialized def discard[A](evalForSideEffectOnly: A): Unit = {
    val _: A = evalForSideEffectOnly
    ()
  }
}
