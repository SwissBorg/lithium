package akka.cluster.sbr.strategy

import akka.cluster.sbr.{StrategyDecision, WorldView}
import cats.implicits._
import cats.kernel.Monoid
import shapeless.{::, Cached, Generic, HList, HNil, Lazy, Strict}

sealed trait DerivedStrategy[A] extends Strategy[A]

object DerivedStrategy {
  def gen[A, Repr](implicit gen: Generic.Aux[A, Repr],
                   strategyRepr: Cached[Strict[DerivedStrategy[Repr]]]): DerivedStrategy[A] = new DerivedStrategy[A] {
    override def handle(strategy: A, worldView: WorldView): Either[Throwable, StrategyDecision] =
      strategyRepr.value.value.handle(gen.to(strategy), worldView)
  }

  implicit def hconsDerivedStrategy[H, Repr <: HList, T: HList](
    implicit gen: Generic.Aux[H, Repr],
    R: Lazy[DerivedStrategy[Repr]],
    T: DerivedStrategy[T]
  ): DerivedStrategy[H :: T] = new DerivedStrategy[H :: T] {
    override def handle(strategy: H :: T, worldView: WorldView): Either[Throwable, StrategyDecision] =
      for {
        a <- R.value.handle(gen.to(strategy.head), worldView)
        b <- T.handle(strategy.tail, worldView)
      } yield a |+| b
  }

  implicit val hnilDerivedStrategy: DerivedStrategy[HNil] = new DerivedStrategy[HNil] {
    override def handle(strategy: HNil, worldView: WorldView): Either[Throwable, StrategyDecision] =
      Monoid[StrategyDecision].empty.asRight
  }
}
