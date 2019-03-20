package akka.cluster.sbr

sealed abstract class Status  extends Product with Serializable
final case object Reachable   extends Status
final case object Unreachable extends Status
final case object Staged      extends Status
