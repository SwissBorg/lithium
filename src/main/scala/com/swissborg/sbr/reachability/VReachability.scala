package com.swissborg.sbr
package reachability

import akka.cluster.UniqueAddress
import cats.data.NonEmptyMap
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.DetectionProtest.Unprotested

/**
  * ADT representing the reachability of a member.
  */
sealed abstract private[reachability] class VReachability {

  /**
    * True when it has already been retrieved.
    * Used to only notify in case of a change.
    */
  val hasBeenRetrieved: Boolean

  /**
    * Set [[hasBeenRetrieved]] to true.
    */
  def tagAsRetrieved: VReachability

  /**
    * Protest the observation of this member being unreachable by `observer`.
    * The version must be strictly increasing between protests.
    */
  def withProtest(protester: Protester, observer: Observer, version: Version): VReachability

  /**
    * Set this member as unreachable from `observer`.
    * The version must be strictly increasing between protests.
    */
  def withUnreachableFrom(observer: Observer, version: Version): VReachability

  /**
    * Forget all the protests done by `protester` towards `observer`.
    */
  def withoutProtest(protester: Protester, observer: Observer, version: Version): VReachability

  /**
    * Remove all mentions of `node`.
    */
  def remove(node: UniqueAddress): VReachability
}

private[reachability] object VReachability {
  def unreachableFrom(observer: Observer, version: Version): VReachability =
    VUnreachable.fromDetection(observer, version)

  private[reachability] def allUnprotested(
      detections: NonEmptyMap[Observer, DetectionProtest]
  ): Boolean =
    detections.forall {
      case _: Unprotested => true
      case _              => false
    }

}

/**
  * A member that is reachable
  */
private[reachability] final case class VReachable private (hasBeenRetrieved: Boolean)
    extends VReachability {
  override lazy val tagAsRetrieved: VReachability = copy(hasBeenRetrieved = true)

  override def withProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability =
    VIndirectlyConnected.fromProtest(protester, observer, version)

  override def withUnreachableFrom(observer: Observer, version: Version): VReachability =
    VUnreachable.fromDetection(observer, version)

  override def withoutProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = this

  override def remove(node: UniqueAddress): VReachability = this
}

private object VReachable {
  val notRetrieved: VReachability = VReachable(hasBeenRetrieved = false)
}

/**
  * A member that is indirectly connected. At least one of the unreachability detections has been been contested.
  */
private[reachability] final case class VIndirectlyConnected private (
    detections: NonEmptyMap[Observer, DetectionProtest],
    hasBeenRetrieved: Boolean
) extends VReachability {
  override lazy val tagAsRetrieved: VReachability = copy(hasBeenRetrieved = true)

  override def withProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val updatedDetection =
      detections
        .lookup(observer)
        .fold(DetectionProtest.protested(protester, version))(_.addProtester(protester, version))

    copy(detections = detections.add(observer -> updatedDetection))
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  override def withUnreachableFrom(observer: Observer, version: Version): VReachability = {
    val updatedDetection =
      detections.lookup(observer).fold(DetectionProtest.unprotested(version))(_.detection(version))

    // `observer` is not protesting any of the observations anymore
    val updatedDetections =
      detections.map(_.pruneProtester(observer)).add(observer -> updatedDetection)

    val isUnprotested = updatedDetections.forall {
      case _: DetectionProtest.Protested   => false
      case _: DetectionProtest.Unprotested => true
    }

    if (isUnprotested) {
      VUnreachable(
        updatedDetections.asInstanceOf[NonEmptyMap[Observer, DetectionProtest.Unprotested]]
      )
    } else {
      copy(detections = updatedDetections)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  override def withoutProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val updatedDetections = detections.updateWith(observer)(_.removeProtester(protester, version))

    if (VReachability.allUnprotested(updatedDetections)) {
      // We are sure that it only contains `Unprotested` values.
      VUnreachable(
        updatedDetections.asInstanceOf[NonEmptyMap[Observer, DetectionProtest.Unprotested]]
      )
    } else {
      copy(detections = updatedDetections)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  override def remove(node: UniqueAddress): VReachability = {
    val updatedDetections = (detections - node).map {
      case (observer, protest) =>
        observer -> protest.pruneProtester(node)
    }

    NonEmptyMap.fromMap(updatedDetections).fold(VReachable.notRetrieved) { detections =>
      if (VReachability.allUnprotested(detections)) {
        // We are sure that it only contains `Unprotested` values.
        VUnreachable(detections.asInstanceOf[NonEmptyMap[Observer, DetectionProtest.Unprotested]])
      } else {
        copy(detections = detections)
      }
    }
  }
}

private object VIndirectlyConnected {
  def apply(detections: NonEmptyMap[Observer, DetectionProtest]): VReachability =
    VIndirectlyConnected(detections, hasBeenRetrieved = false)

  def fromDetection(detection: DetectionProtest, observer: Observer): VReachability =
    detection match {
      case protested: DetectionProtest.Protested =>
        VIndirectlyConnected(NonEmptyMap.of(observer -> protested), hasBeenRetrieved = false)

      case unprotested: DetectionProtest.Unprotested =>
        VUnreachable(NonEmptyMap.of(observer -> unprotested))
    }

  def fromProtest(protester: Protester, observer: Observer, version: Version): VReachability =
    fromDetection(DetectionProtest.Protested.one(protester, version), observer)
}

/**
  * An unreachable member.
  */
private[reachability] final case class VUnreachable private (
    detections: NonEmptyMap[Observer, DetectionProtest.Unprotested],
    hasBeenRetrieved: Boolean
) extends VReachability {
  override lazy val tagAsRetrieved: VReachability = copy(hasBeenRetrieved = true)

  override def withProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val maybeUpdatedProtested =
      detections
        .lookup(observer)
        .fold[Option[DetectionProtest]](Option(DetectionProtest.Protested.one(protester, version))) {
          currentUnprotested =>
            if (currentUnprotested.version == version) {
              // Also when equal because the contention is against an unreachable
              // detection received previously.
              Some(currentUnprotested.addProtester(protester, version))
            } else if (currentUnprotested.version < version) {
              Some(DetectionProtest.Protested.one(protester, version))
            } else {
              None
            }
        }

    maybeUpdatedProtested.fold[VReachability](this) { protested =>
      VIndirectlyConnected.fromDetection(protested, observer)
    }
  }

  override def withUnreachableFrom(observer: Observer, version: Version): VReachability = {
    val updatedUnprotested =
      detections.lookup(observer).fold(DetectionProtest.Unprotested(version)) {
        currentUnprotested =>
          if (currentUnprotested.version < version) {
            DetectionProtest.Unprotested(version)
          } else {
            currentUnprotested
          }
      }

    copy(detections = detections.add(observer -> updatedUnprotested))
  }

  override def withoutProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val updatedDetections = detections - observer

    NonEmptyMap.fromMap(updatedDetections).fold(VReachable.notRetrieved)(d => copy(detections = d))
  }

  override def remove(node: UniqueAddress): VReachability = {
    // No need to remove it from the `Unprotested` keys.
    val updatedDetections = detections - node

    NonEmptyMap.fromMap(updatedDetections).fold(VReachable.notRetrieved)(d => copy(detections = d))
  }
}

private object VUnreachable {
  def fromDetection(observer: Observer, version: Version): VUnreachable =
    VUnreachable(
      NonEmptyMap.of(observer -> DetectionProtest.Unprotested(version)),
      hasBeenRetrieved = false
    )

  def apply(detections: NonEmptyMap[Observer, DetectionProtest.Unprotested]): VReachability =
    VUnreachable(detections, hasBeenRetrieved = false)
}
