package akka.cluster.sbr

import akka.cluster.UniqueAddress
import akka.cluster.sbr.SBFailureDetector.{UnreachabilityContention => _, _}
import akka.cluster.sbr.SBFailureDetectorState._

/**
 * State of the SBRFailureDetector.
 */
final private[sbr] case class SBFailureDetectorState private (
  reachabilities: Map[Subject, VersionedReachability],
  contentions: Map[Subject, Map[Observer, UnreachabilityContention]]
) {

  /**
   * Returns the `subject`'s status if it has changed since the last time
   * this method was called.
   *
   * The status is `None` when it has not changed since the last status retrieval.
   */
  def updatedStatus(subject: Subject): (Option[SBRReachability], SBFailureDetectorState) =
    reachabilities
      .get(subject)
      .map { r =>
        if (!r.retrieved) {
          // The reachability has changed since the last retrieval
          (Some(r.reachability), copy(reachabilities = reachabilities + (subject -> r.tagAsRetrieved)))
        } else {
          (None, this)
        }
      }
      .getOrElse {
        // The node is seen for the 1st time.
        // It is reachable by default.
        val r = reachable(subject)
        (Some(Reachable),
         r.copy(reachabilities = r.reachabilities + (subject -> r.reachabilities(subject).tagAsRetrieved)))
      }

  /**
   * Sets the `subject` as reachable.
   */
  def reachable(subject: Subject): SBFailureDetectorState =
    copy(reachabilities = reachabilities + (subject -> updateReachability(subject, Reachable)),
         contentions = contentions - subject)

  /**
   * Sets `node` as unreachable and removes the current node
   * from all the related contentions.
   */
  def unreachable(observer: Observer, subject: Subject): SBFailureDetectorState = {
    // The observer node now agrees.
    val updatedContentions =
      contentions
        .get(subject)
        .map { cs =>
          contentions + (subject -> cs.flatMap {
            case (o, c) =>
              if (c.disagreeing.size == 1 && c.disagreeing(observer)) {
                None // No one disagrees after removing the `observer`.
              } else {
                Some(o -> c.agree(observer))
              }
          })
        }
        .getOrElse(contentions) // nothing to do as there's no ongoing contention

    // The node is being tagged as unreachable. So if no contention exists for it we can
    // safely assume it is unreachable. It is either unreachable or indirectly connected.
    val isUnreachable = updatedContentions.get(subject).forall(_.values.forall(_.disagreeing.isEmpty))

    if (isUnreachable) {
      copy(reachabilities = reachabilities + (subject -> updateReachability(subject, Unreachable)),
           contentions = updatedContentions - subject)
    } else {
      copy(reachabilities = reachabilities + (subject -> updateReachability(subject, IndirectlyConnected)),
           contentions = updatedContentions)
    }
  }

  private def updateReachability(subject: Subject, reachability: SBRReachability): VersionedReachability =
    reachabilities
      .get(subject)
      .map(_.update(reachability))
      .getOrElse(VersionedReachability.init(reachability))

  /**
   * Updates the contention of the observation of `subject` by `observer`
   * by the cluster node `node`.
   */
  def contention(node: UniqueAddress, observer: Observer, subject: Subject, version: Long): SBFailureDetectorState = {
    val contentions0 = contentions.getOrElse(subject, Map.empty)
    val contention   = contentions0.getOrElse(observer, UnreachabilityContention.empty)

    if (contention.version == version) {
      copy(
        reachabilities = indirectlyConnected(subject),
        contentions = contentions + (subject -> (contentions0 + (observer -> contention.disagree(node))))
      )
    } else if (contention.version < version) {
      // First contention for this version.
      // Forget the old version and create a new one.
      copy(
        reachabilities = indirectlyConnected(subject),
        contentions = contentions + (subject -> (contentions0 + (observer -> UnreachabilityContention(Set(node),
                                                                                                      version))))
      )
    } else {
      // Ignore the contention. It is for an older
      // detection of `subject` by `observer`.
      this
    }
  }

  def remove(node: UniqueAddress): SBFailureDetectorState = {
    val updatedM = (contentions - node).mapValues { observers =>
      (observers - node).mapValues(_.agree(node))
    }

    val updatedReachabilities = updatedM.flatMap {
      case (subject, contentions) =>
        if (contentions.isEmpty) {
          // No contentions are left for the subject
          // as the disputed observer has been removed.
          // The subject becomes reachable.
          Some(subject -> updateReachability(subject, Reachable))
        } else if (contentions.nonEmpty && contentions.values.forall(_.disagreeing.isEmpty)) {
          // There are still disputed observers but everyone agrees with them.
          // The subject becomes unreachable.
          Some(subject -> updateReachability(subject, Unreachable))
        } else {
          None
        }
    }

    copy(
      reachabilities = reachabilities ++ updatedReachabilities - node,
      // Subjects whose reachabilities have been updated are removed
      // as for all of them there is no disputed observer or all the
      // observers agree.
      contentions = updatedM -- updatedReachabilities.keySet
    )
  }

  private def indirectlyConnected(subject: Subject): Map[UniqueAddress, VersionedReachability] = {
    val diff = reachabilities
      .get(subject)
      .map(_.update(IndirectlyConnected))
      .getOrElse(VersionedReachability.init(IndirectlyConnected))

    reachabilities + (subject -> diff)
  }
}

private[sbr] object SBFailureDetectorState {
  type Observer = UniqueAddress
  type Subject  = UniqueAddress

  val empty: SBFailureDetectorState = SBFailureDetectorState(Map.empty, Map.empty)

  /**
   * Represents the reachability of a node.
   */
  final case class VersionedReachability(reachability: SBRReachability, retrieved: Boolean) {
    // lazy else the computation of the hashcode explodes
    lazy val tagAsRetrieved: VersionedReachability = copy(retrieved = true)

    def update(r: SBRReachability): VersionedReachability =
      if (r != reachability) copy(reachability = r, retrieved = false) else this
  }

  object VersionedReachability {
    def init(r: SBRReachability): VersionedReachability = VersionedReachability(r, retrieved = false)
  }

  /**
   * Represents a contention against a detection by a node. The `version`
   * needs to be strictly increasing for each `observer`, `subject` pair.
   */
  final case class UnreachabilityContention(disagreeing: Set[UniqueAddress], version: Long) {
    def disagree(node: UniqueAddress): UnreachabilityContention = copy(disagreeing = disagreeing + node)
    def agree(node: UniqueAddress): UnreachabilityContention    = copy(disagreeing = disagreeing - node)
  }

  object UnreachabilityContention {
    val empty: UnreachabilityContention = UnreachabilityContention(Set.empty, 0)
  }
}
