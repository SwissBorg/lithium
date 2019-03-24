package akka.cluster.sbr.strategy

object auto {
  implicit def autoGen[A, Repr]: DerivedStrategy[A] = DerivedStrategy.gen
}
