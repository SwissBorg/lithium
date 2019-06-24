package com.swissborg.sbr.instances

import cats.Applicative
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Gen.const

trait ApplicativeTestInstances {
  implicit val genApplicative: Applicative[Gen] = new Applicative[Gen] {
    override def pure[A](x: A): Gen[A] = const(x)
    override def ap[A, B](ff: Gen[A => B])(fa: Gen[A]): Gen[B] =
      for {
        ff <- ff
        a <- fa
      } yield ff(a)
  }

  implicit val arbitraryApplicative: Applicative[Arbitrary] = new Applicative[Arbitrary] {
    override def pure[A](x: A): Arbitrary[A] = Arbitrary(Applicative[Gen].pure(x))
    override def ap[A, B](ff: Arbitrary[A => B])(fa: Arbitrary[A]): Arbitrary[B] =
      Arbitrary(Applicative[Gen].ap(ff.arbitrary)(fa.arbitrary))
  }
}
