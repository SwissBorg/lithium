package com.swissborg

package object sbr {
  @specialized def discard[A](evalForSideEffectOnly: A): Unit = {
    val _: A = evalForSideEffectOnly
    ()
  }
}
