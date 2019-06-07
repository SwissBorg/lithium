package com.swissborg.sbr.reachability

import cats.data.{NonEmptyMap, NonEmptySet}
import com.swissborg.sbr.implicits._

sealed abstract private[reachability] class Detection {
  val version: Version

  def addProtester(protester: Protester): Detection.Protested
  def removeProtester(protester: Protester): Detection
}

private[reachability] object Detection {
  final case class Protested(protesters: NonEmptySet[Protester], version: Version)
      extends Detection {
    override def addProtester(protester: Protester): Protested =
      copy(protesters = protesters.add(protester))

    override def removeProtester(protester: Protester): Detection =
      NonEmptySet
        .fromSet(protesters - protester)
        .fold(unprotested(version))(p => copy(protesters = p))
  }

  object Protested {
    def one(protester: Protester, version: Version): Protested =
      Protested(NonEmptySet.one(protester), version)
  }

  final case class Unprotested(version: Version) extends Detection {
    override def addProtester(protester: Protester): Protested = Protested.one(protester, version)

    override def removeProtester(protester: Protester): Detection = this
  }

  def protested(protester: Protester, version: Version): Detection =
    Protested.one(protester, version)

  def allUnprotested(detections: NonEmptyMap[Observer, Detection]): Boolean =
    detections.forall {
      case _: Unprotested => true
      case _              => false
    }

  def unprotested(version: Version): Detection = Unprotested(version)
}
