package com.swissborg.sbr.reachability

import akka.cluster.UniqueAddress
import cats.data.NonEmptyMap
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.Detection.allUnprotested

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
  def withoutProtest(protester: Protester, observer: Observer): VReachability

  /**
    * Remove all mentions of `node`.
    */
  def remove(node: UniqueAddress): VReachability
}

private[reachability] object VReachability {
  def unreachableFrom(observer: Observer, version: Version): VReachability =
    VUnreachable.fromDetection(observer, version)
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

  override def withoutProtest(protester: Protester, observer: Observer): VReachability = this

  override def remove(node: UniqueAddress): VReachability = this
}

private object VReachable {
  val notRetrieved: VReachability = VReachable(hasBeenRetrieved = false)
}

/**
  * A member that is indirectly connected. At least one of the unreachability detections has been been contested.
  */
private[reachability] final case class VIndirectlyConnected private (
    detections: NonEmptyMap[Observer, Detection],
    hasBeenRetrieved: Boolean
) extends VReachability {
  override lazy val tagAsRetrieved: VReachability = copy(hasBeenRetrieved = true)

  override def withProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val updatedDetection =
      detections.lookup(observer).fold(Detection.protested(protester, version)) { currentProtest =>
        if (currentProtest.version < version) {
          // protest has a newer version, start a new protest
          Detection.protested(protester, version)
        } else if (currentProtest.version == version) {
          // protest is for the current detection, update it
          currentProtest.addProtester(protester)
        } else {
          // protest is for an old detection
          currentProtest
        }
      }

    copy(detections = detections.add(observer -> updatedDetection))
  }

  override def withUnreachableFrom(observer: Observer, version: Version): VReachability = {
    val updatedDetection =
      detections.lookup(observer).fold[Detection](Detection.Unprotested(version)) {
        currentProtest =>
          if (currentProtest.version < version) {
            // detection has a newer version, start a new empty protest
            Detection.Unprotested(version)
          } else {
            // a newer detection has already been received
            currentProtest
          }
      }

    // `observer` is not protesting any of the observations anymore
    val updatedDetections =
      detections.map(_.removeProtester(observer)).add(observer -> updatedDetection)

    val isUnprotested = updatedDetections.forall {
      case _: Detection.Protested   => false
      case _: Detection.Unprotested => true
    }

    if (isUnprotested) {
      VUnreachable(updatedDetections.asInstanceOf[NonEmptyMap[Observer, Detection.Unprotested]])
    } else {
      copy(detections = updatedDetections)
    }
  }

  override def withoutProtest(protester: Protester, observer: Observer): VReachability = {
    val updatedDetections = detections.updateWith(observer)(_.removeProtester(protester))

    if (allUnprotested(updatedDetections)) {
      // We are sure that it only contains `Unprotested` values.
      VUnreachable(updatedDetections.asInstanceOf[NonEmptyMap[Observer, Detection.Unprotested]])
    } else {
      copy(detections = updatedDetections)
    }
  }

  override def remove(node: UniqueAddress): VReachability = {
    val updatedDetections = (detections - node).map {
      case (observer, protest) => observer -> protest.removeProtester(node)
    }

    NonEmptyMap.fromMap(updatedDetections).fold(VReachable.notRetrieved) { detections =>
      if (allUnprotested(detections)) {
        // We are sure that it only contains `Unprotested` values.
        VUnreachable(detections.asInstanceOf[NonEmptyMap[Observer, Detection.Unprotested]])
      } else {
        copy(detections = detections)
      }
    }
  }
}

private object VIndirectlyConnected {
  def apply(detections: NonEmptyMap[Observer, Detection]): VReachability =
    VIndirectlyConnected(detections, false)

  def fromProtested(protested: Detection.Protested, observer: Observer): VReachability =
    VIndirectlyConnected(NonEmptyMap.of(observer -> protested), hasBeenRetrieved = false)

  def fromProtest(protester: Protester, observer: Observer, version: Version): VReachability =
    fromProtested(Detection.Protested.one(protester, version), observer)
}

/**
  * An unreachable member.
  */
private[reachability] final case class VUnreachable private (
    detections: NonEmptyMap[Observer, Detection.Unprotested],
    hasBeenRetrieved: Boolean
) extends VReachability {
  override lazy val tagAsRetrieved: VReachability = copy(hasBeenRetrieved = true)

  override def withProtest(
      protester: Protester,
      observer: Observer,
      version: Version
  ): VReachability = {
    val maybeUpdatedProtested =
      detections.lookup(observer).fold(Option(Detection.Protested.one(protester, version))) {
        currentUnprotested =>
          if (currentUnprotested.version == version) {
            // Also when equal because the contention is against an unreachable
            // detection received previously.
            Some(currentUnprotested.addProtester(protester))
          } else if (currentUnprotested.version < version) {
            Some(Detection.Protested.one(protester, version))
          } else {
            None
          }
      }

    maybeUpdatedProtested.fold[VReachability](this) { protested =>
      VIndirectlyConnected.fromProtested(protested, observer)
    }
  }

  override def withUnreachableFrom(observer: Observer, version: Version): VReachability = {
    val updatedUnprotested = detections.lookup(observer).fold(Detection.Unprotested(version)) {
      currentUnprotested =>
        if (currentUnprotested.version < version) {
          Detection.Unprotested(version)
        } else {
          currentUnprotested
        }
    }

    copy(detections = detections.add(observer -> updatedUnprotested))
  }

  override def withoutProtest(protester: Protester, observer: Observer): VReachability = {
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
      NonEmptyMap.of(observer -> Detection.Unprotested(version)),
      hasBeenRetrieved = false
    )

  def apply(detections: NonEmptyMap[Observer, Detection.Unprotested]): VReachability =
    VUnreachable(detections, hasBeenRetrieved = false)
}
