package akka.cluster

package object swissborg {

  /**
   * Helper to work around `wartremover:NonUnitStatements` warts.
   */
  @specialized def discard[A](evalForSideEffectOnly: A): Unit = {
    val _: A = evalForSideEffectOnly
    ()
  }
}
