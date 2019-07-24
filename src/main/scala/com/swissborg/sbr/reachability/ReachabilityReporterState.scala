package com.swissborg.sbr
package reachability

import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterSettings.DataCenter
import akka.cluster.{Member, UniqueAddress}
import cats.data.{NonEmptyMap, State}
import cats.implicits._
import com.swissborg.sbr.reachability.ReachabilityReporter.{
  SuspiciousDetection,
  SuspiciousDetectionAck
}

import scala.collection.immutable.SortedSet

/**
  * State of the SBRFailureDetector.
  */
final private[reachability] case class ReachabilityReporterState private (
    selfDataCenter: DataCenter,
    otherDcMembers: SortedSet[UniqueAddress],
    reachabilities: Map[Subject, VReachability],
    pendingSuspiciousDetectionAcks: Map[UniqueAddress, Set[SuspiciousDetectionAck]]
) {

  /**
    * Set the `subject` as reachable.
    */
  private[reachability] def withReachable(subject: Subject): ReachabilityReporterState =
    _withReachable(subject, tagAsRetrieved = false)

  /**
    * Set the `subject` as reachable and already tag its reachability as retrieved
    */
  private[reachability] def withReachableAndSetAsRetrieved(
      subject: Subject
  ): ReachabilityReporterState =
    _withReachable(subject, tagAsRetrieved = true)

  /**
    * Set the `subject` as reachable.
    */
  private def _withReachable(
      subject: Subject,
      tagAsRetrieved: Boolean
  ): ReachabilityReporterState = {
    val reachable = VReachable.notRetrieved
    copy(
      reachabilities = reachabilities + (subject -> (if (tagAsRetrieved) reachable.tagAsRetrieved
                                                     else reachable))
    )
  }

  /**
    * Set the `subject` as unreachable from the `observer`.
    * The version must be non-decreasing for each `observer`-`subject` pair.
    */
  private[reachability] def withUnreachableFrom(
      observer: Observer,
      subject: Subject,
      version: Version
  ): ReachabilityReporterState = {
    val updatedReachability = reachabilities
      .get(subject)
      .fold(VReachability.unreachableFrom(observer, version))(
        _.withUnreachableFrom(observer, version)
      )

    copy(reachabilities = reachabilities + (subject -> updatedReachability))
  }

  /**
    * Update the suspicious detection of the observation of `subject` by `observer`
    * by the cluster node `node`.
    */
  private[reachability] def withSuspiciousDetection(
      protester: Protester,
      observer: Observer,
      subject: Subject,
      version: Version
  ): ReachabilityReporterState =
    copy(
      reachabilities = reachabilities + (subject -> reachabilities
        .get(subject)
        .fold(VIndirectlyConnected.fromProtest(protester, observer, version))(
          _.withProtest(protester, observer, version)
        ))
    )

  private[reachability] def withoutSuspiciousDetection(
      protester: Protester,
      observer: Observer,
      subject: Subject,
      version: Version
  ): ReachabilityReporterState = {
    val updatedReachabilities = reachabilities + (subject -> reachabilities
      .get(subject)
      .fold(VReachable.notRetrieved)(_.withoutProtest(protester, observer, version)))

    copy(reachabilities = updatedReachabilities)
  }

  private[reachability] def add(member: Member): ReachabilityReporterState =
    if (member.dataCenter =!= selfDataCenter) {
      copy(otherDcMembers = otherDcMembers + member.uniqueAddress)
    } else {
      this
    }

  /**
    * Remove the node.
    */
  private[reachability] def remove(node: UniqueAddress): ReachabilityReporterState =
    copy(
      otherDcMembers = otherDcMembers - node, // no need to check DC
      reachabilities = (reachabilities - node).map {
        case (subject, reachability) => subject -> reachability.remove(node)
      },
      pendingSuspiciousDetectionAcks = pendingSuspiciousDetectionAcks - node
    )

  private[reachability] def expectSuspiciousDetectionAck(
      ack: SuspiciousDetectionAck
  ): ReachabilityReporterState =
    copy(
      pendingSuspiciousDetectionAcks = pendingSuspiciousDetectionAcks + (ack.from -> (pendingSuspiciousDetectionAcks
        .getOrElse(ack.from, Set.empty) + ack))
    )

  private[reachability] def registerSuspiciousDetectionAck(
      ack: SuspiciousDetectionAck
  ): ReachabilityReporterState = {
    val updatedPendingSuspiciousDetectionAcks = pendingSuspiciousDetectionAcks
      .get(ack.from)
      .fold(pendingSuspiciousDetectionAcks) { pendingAcks =>
        val newPendingAcks = pendingAcks - ack

        if (newPendingAcks.isEmpty) pendingSuspiciousDetectionAcks - ack.from
        else pendingSuspiciousDetectionAcks + (ack.from -> newPendingAcks)
      }
    copy(
      pendingSuspiciousDetectionAcks = updatedPendingSuspiciousDetectionAcks
    )
  }

  private[reachability] def allSuspiciousDetections: Set[SuspiciousDetection] = {
    def extractDetections(
        subject: Subject,
        detections: NonEmptyMap[Observer, DetectionProtest]
    ): Iterator[SuspiciousDetection] =
      detections.toSortedMap.iterator.flatMap {
        case (observer, DetectionProtest.Protested(protesters, version)) =>
          protesters.toSortedSet.iterator
            .map(p => SuspiciousDetection(p, observer, subject, version))
        case _ => Iterator.empty
      }

    reachabilities.iterator.flatMap {
      case (_, VReachable(_))                             => Iterator.empty
      case (subject, VIndirectlyConnected(detections, _)) => extractDetections(subject, detections)
      case (subject, VUnreachable(detections, _))         => extractDetections(subject, detections)
    }.toSet
  }
}

private[reachability] object ReachabilityReporterState {
  def apply(selfDataCenter: DataCenter): ReachabilityReporterState =
    ReachabilityReporterState(selfDataCenter, SortedSet.empty, Map.empty, Map.empty)

  def fromSnapshot(
      snapshot: CurrentClusterState,
      selfDataCenter: DataCenter
  ): ReachabilityReporterState =
    snapshot.members.foldLeft(ReachabilityReporterState(selfDataCenter))(_.add(_))

  /**
    * Return the `subject`'s reachability if it has changed since the last time
    * this method was called.
    *
    * The status is `None` when it has not changed since the last reachability retrieval.
    */
  private[reachability] def updatedReachability(
      subject: Subject
  ): State[ReachabilityReporterState, Option[ReachabilityStatus]] = State { s =>
    s.reachabilities
      .get(subject)
      .fold(
        (
          s.withReachableAndSetAsRetrieved(subject),
          Option[ReachabilityStatus](ReachabilityStatus.Reachable)
        )
      ) { reachability =>
        if (reachability.hasBeenRetrieved) {
          (s, None)
        } else {
          (
            s.copy(reachabilities = s.reachabilities + (subject -> reachability.tagAsRetrieved)),
            Some(reachability.toSBReachabilityStatus)
          )
        }
      }
  }

  /**
    * Return all the changed reachabilities since the last time this method was called.
    */
  private[reachability] val allUpdatedReachabilies
      : State[ReachabilityReporterState, Map[Subject, ReachabilityStatus]] = State { s =>
    s.reachabilities
      .foldLeft((s, Map.empty[Subject, ReachabilityStatus])) {
        case ((s, statuses), (subject, reachability)) =>
          if (reachability.hasBeenRetrieved) {
            (s, statuses)
          } else {
            (
              s.copy(reachabilities = s.reachabilities + (subject -> reachability.tagAsRetrieved)),
              statuses + (subject -> reachability.toSBReachabilityStatus)
            )
          }
      }
  }
}
