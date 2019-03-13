package akka.cluster.sbr

import cats.Applicative
import org.scalacheck.Arbitrary
import org.scalacheck.Gen.const

object implicits {
  implicit val arbitraryApplicative: Applicative[Arbitrary] = new Applicative[Arbitrary] {
    override def pure[A](x: A): Arbitrary[A] = Arbitrary(const(x))
    override def ap[A, B](ff: Arbitrary[A => B])(fa: Arbitrary[A]): Arbitrary[B] =
      Arbitrary(for {
        ff <- ff.arbitrary
        a  <- fa.arbitrary
      } yield ff(a))
  }
}
