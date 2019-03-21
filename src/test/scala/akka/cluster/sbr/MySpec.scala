package akka.cluster.sbr

import cats.Eq
import cats.instances._
import cats.syntax._
import cats.tests.{StrictCatsEquality, TestSettings}
import org.scalactic.anyvals._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.Discipline

trait MySpec
    extends FreeSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with TestSettings
    with AllInstances
    with AllInstancesBinCompat0
    with AllInstancesBinCompat1
    with AllInstancesBinCompat2
    with AllInstancesBinCompat3
    with AllSyntax
    with AllSyntaxBinCompat0
    with AllSyntaxBinCompat1
    with AllSyntaxBinCompat2
    with AllSyntaxBinCompat3
    with AllSyntaxBinCompat4
    with StrictCatsEquality {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = PosInt(1000),
                               maxDiscardedFactor = PosZDouble(5),
                               minSize = PosZInt(0),
                               sizeRange = PosZInt(100),
                               workers = PosInt(8))

  // disable Eq syntax (by making `catsSyntaxEq` not implicit), since it collides
  // with scalactic's equality
  override def catsSyntaxEq[A: Eq](a: A): EqOps[A] = new EqOps[A](a)

//    PropertyCheckConfiguration(minSuccessful = PosInt(10000),
//                               maxDiscardedFactor = PosZDouble(5),
//                               minSize = PosZInt(0),
//                               sizeRange = PosZInt(100),
//                               workers = PosInt(8))

//    PropertyCheckConfig(minSuccessful = 5000, maxDiscarded = 250000, maxSize = 100, workers = 8)
//    PropertyCheckConfig(minSuccessful = 100)
}
