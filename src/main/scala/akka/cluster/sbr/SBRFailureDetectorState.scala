package akka.cluster.sbr

import akka.cluster.UniqueAddress
import akka.cluster.sbr.SBRFailureDetector._
import akka.cluster.sbr.SBRFailureDetectorState.{Contention, VersionedReachability}

/**
 * State of the SBRFailureDetector.
 */
final private[sbr] case class SBRFailureDetectorState private (
  selfUniqueAddress: UniqueAddress,
  reachabilities: Map[UniqueAddress, VersionedReachability],
  contentions: Map[UniqueAddress, Map[UniqueAddress, Contention]]
) {

  /**
   * Returns `node`'s status and the updated state.
   *
   * The status is `None` when it has not changed since the last status retrieval.
   */
  def status(node: UniqueAddress): (Option[SBRReachability], SBRFailureDetectorState) =
    reachabilities
      .get(node)
      .map { r =>
        r.previous
          .map { previous =>
            if (previous != r.current) {
              // The reachabiltiy has changed since the last retrieval
              (Some(r.current), copy(reachabilities = reachabilities + (node -> r.advance)))
            } else {
              (None, this)
            }
          }
          .getOrElse(
            // First retrieval
            (Some(r.current), copy(reachabilities = reachabilities + (node -> r.advance)))
          )
      }
      .getOrElse {
        // The node is seen for the 1st time.
        // It is reachable by default.
        (Some(Reachable), reachable(node))
      }

  /**
   * Sets `node` as reachable.
   */
  def reachable(node: UniqueAddress): SBRFailureDetectorState = {
    val vReachability = reachabilities
      .get(node)
      .map(_.update(Reachable))
      .getOrElse(VersionedReachability(None, Reachable))

    copy(reachabilities = reachabilities + (node -> vReachability))
  }

  /**
   * Sets `node` as unreachable and removes the current node
   * from all the related contentions.
   */
  def unreachable(node: UniqueAddress): SBRFailureDetectorState = {
    // This node now agrees.
    val updatedContentions =
      contentions
        .get(node)
        .map { cs =>
          contentions + (node -> cs.flatMap {
            case (observer, c) =>
              if (c.disagreeing.size == 1 && c.disagreeing(selfUniqueAddress)) {
                None // remove the contention
              } else {
                Some(observer -> c.agree(selfUniqueAddress))
              }
          })
        }
        .getOrElse(contentions) // nothing to do as there's no ongoing contention

    // The node is being tagged as unreachable. So if no contention exists for it we can
    // safely assume it is unreachable. It is either unreachable or indirectly connected.
    val isUnreachable = updatedContentions.get(node).forall(_.values.forall(_.disagreeing.isEmpty))

    if (isUnreachable) {
      val updatedVReachability = reachabilities
        .get(node)
        .map(_.update(Unreachable))
        .getOrElse(VersionedReachability.init(Unreachable))

      copy(reachabilities = reachabilities + (node -> updatedVReachability), contentions = updatedContentions)
    } else {
      copy(contentions = updatedContentions)
    }
  }

  /**
   * Updates the contention of the observation of `subject` by `observer`
   * by the cluster node `node`.
   */
  def contention(node: UniqueAddress,
                 observer: UniqueAddress,
                 subject: UniqueAddress,
                 version: Long): SBRFailureDetectorState = {
    val contentions0 = contentions.getOrElse(subject, Map.empty)
    val contention   = contentions0.getOrElse(observer, Contention.empty)

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
        contentions = contentions + (subject -> (contentions0 + (observer -> Contention(Set(node), version))))
      )
    } else {
      // Ignore the contention. It is for an older
      // detection of `subject` by `observer`.
      this
    }
  }

  def removeSubject(subject: UniqueAddress): SBRFailureDetectorState = {
    val updatedM = (contentions - subject).mapValues(_.mapValues(ic => ic.agree(subject)))
    copy(reachabilities = reachabilities - subject, contentions = updatedM)
  }

  def removeObserver(observer: UniqueAddress): SBRFailureDetectorState = {
    val updatedM = contentions.mapValues { observers =>
      (observers - observer).mapValues(ic => ic.agree(observer))
    }
    copy(reachabilities = reachabilities - observer, contentions = updatedM)
  }

  private def indirectlyConnected(subject: UniqueAddress): Map[UniqueAddress, VersionedReachability] = {
    val diff = reachabilities
      .get(subject)
      .map(_.update(IndirectlyConnected))
      .getOrElse(VersionedReachability(None, IndirectlyConnected))

    reachabilities + (subject -> diff)
  }
}

private[sbr] object SBRFailureDetectorState {
  def apply(selfUniqueAddress: UniqueAddress): SBRFailureDetectorState =
    SBRFailureDetectorState(selfUniqueAddress, Map.empty, Map.empty)

  /**
   * Represents the reachability of a node.
   * `previous` is used to detect a change in reachability when retrieving the nodes status.
   */
  final case class VersionedReachability(previous: Option[SBRReachability], current: SBRReachability) {
    lazy val advance: VersionedReachability               = copy(previous = Some(current))
    def update(r: SBRReachability): VersionedReachability = copy(previous = Some(current), current = r)
  }

  object VersionedReachability {
    def init(r: SBRReachability): VersionedReachability = VersionedReachability(None, r)
  }

  /**
   * Represents a contention against a detection by a node. The `version`
   * needs to be strictly increasing for each `observer`, `subject` pair.
   */
  final case class Contention(disagreeing: Set[UniqueAddress], version: Long) {
    def disagree(node: UniqueAddress): Contention = copy(disagreeing = disagreeing + node)
    def agree(node: UniqueAddress): Contention    = copy(disagreeing = disagreeing - node)
  }

  object Contention {
    val empty: Contention = Contention(Set.empty, 0)
  }
}
