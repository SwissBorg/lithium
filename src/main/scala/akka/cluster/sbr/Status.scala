//package akka.cluster.sbr
//
//import cats.Eq
//
//sealed abstract class Status extends Product with Serializable
//
//object Status {
//  implicit val statusEq: Eq[Status] = Eq.fromUniversalEquals
//}
//
//final case object Reachable       extends Status
//final case object WeaklyReachable extends Status
//final case object Staged          extends Status
//final case object Unreachable     extends Status
