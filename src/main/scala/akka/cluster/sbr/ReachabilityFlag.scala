package akka.cluster.sbr

sealed abstract class ReachabilityTag extends Product with Serializable
final case object Reachable extends ReachabilityTag
final case object Unreachable extends ReachabilityTag
