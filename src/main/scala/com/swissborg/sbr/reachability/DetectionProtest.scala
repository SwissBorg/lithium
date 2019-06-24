package com.swissborg.sbr
package reachability

import cats.data._
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.DetectionProtest.{
  CurrentVersion,
  NewVersion,
  OldVersion,
  VersionComparison
}

/**
  * Represents protests of unreachabily detections.
  */
sealed abstract private[reachability] class DetectionProtest { self =>

  /**
    * The version of the detection. It must be non-decreasing.
    * A detection with a greater version is a newer detection.
    */
  val version: Version

  /**
    * Add `protester` as a protester of the detection.
    */
  def addProtester(protester: Protester, version: Version): DetectionProtest

  /**
    * Remove `protester` from the protesters of the detection.
    */
  def removeProtester(protester: Protester, version: Version): DetectionProtest

  /**
    * Remove the protester from independently of the version.
    */
  def pruneProtester(protester: Protester): DetectionProtest = removeProtester(protester, version)

  /**
    * When newer, update the detection with the version.
    */
  def detection(version: Version): DetectionProtest =
    if (self.version < version) {
      DetectionProtest.unprotested(version)
    } else {
      self
    }

  protected def compareVersion(version: Version): VersionComparison =
    if (self.version < version) {
      NewVersion
    } else if (self.version > version) {
      OldVersion
    } else {
      CurrentVersion
    }
}

private[reachability] object DetectionProtest {

  /**
    * A detection that has been protested by at-least one node.
    *
    * @param protesters the protesters of the detection.
    * @param version the version of the protest.
    */
  final case class Protested(protesters: NonEmptySet[Protester], version: Version)
      extends DetectionProtest {
    override def addProtester(protester: Protester, version: Version): DetectionProtest =
      compareVersion(version) match {
        case NewVersion     => Protested.one(protester, version)
        case CurrentVersion => copy(protesters = protesters.add(protester))
        case OldVersion     => this
      }

    override def removeProtester(protester: Protester, version: Version): DetectionProtest =
      compareVersion(version) match {
        case NewVersion => Unprotested(version)

        case CurrentVersion =>
          NonEmptySet
            .fromSet(protesters - protester)
            .fold(unprotested(version))(p => copy(protesters = p))

        case OldVersion => this
      }
  }

  object Protested {
    def one(protester: Protester, version: Version): Protested =
      Protested(NonEmptySet.one(protester), version)
  }

  /**
    * A detection that does not have any protesters
    *
    * @param version the version of the detection.
    */
  final case class Unprotested(version: Version) extends DetectionProtest {
    override def addProtester(protester: Protester, version: Version): DetectionProtest =
      compareVersion(version) match {
        case NewVersion | CurrentVersion => Protested.one(protester, version)
        case OldVersion                  => this
      }

    override def removeProtester(protester: Protester, version: Version): DetectionProtest =
      compareVersion(version) match {
        case NewVersion                  => Unprotested(version)
        case OldVersion | CurrentVersion => this
      }
  }

  def protested(protester: Protester, version: Version): DetectionProtest =
    Protested.one(protester, version)

  def unprotested(version: Version): DetectionProtest = Unprotested(version)

  private[reachability] sealed abstract class VersionComparison

  private[reachability] case object NewVersion extends VersionComparison
  private[reachability] case object CurrentVersion extends VersionComparison
  private[reachability] case object OldVersion extends VersionComparison
}
