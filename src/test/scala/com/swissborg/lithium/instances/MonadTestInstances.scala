package com.swissborg.lithium

package instances

import cats.Monad
import org.scalacheck.{Arbitrary, Gen}

trait MonadTestInstances {
  implicit val arbitraryMonad: Monad[Arbitrary] = new Monad[Arbitrary] {
    override def pure[A](x: A): Arbitrary[A] = Arbitrary(Gen.const(x))

    override def flatMap[A, B](fa: Arbitrary[A])(f: A => Arbitrary[B]): Arbitrary[B] =
      Arbitrary(fa.arbitrary.flatMap(f.andThen(_.arbitrary)))

    override def tailRecM[A, B](a: A)(f: A => Arbitrary[Either[A, B]]): Arbitrary[B] =
      Arbitrary(Gen.tailRecM(a)(f.andThen(_.arbitrary)))
  }
}
