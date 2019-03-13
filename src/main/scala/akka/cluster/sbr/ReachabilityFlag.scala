package akka.cluster.sbr

sealed abstract class Reachability extends Product with Serializable
final case object Reachable        extends Reachability
final case object Unreachable      extends Reachability
