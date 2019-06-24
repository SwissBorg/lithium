package akka.cluster.swissborg.instances

import akka.cluster.Reachability.ReachabilityStatus
import cats.Eq

trait EqInstances {
  implicit val reachabilityStatusEq: Eq[ReachabilityStatus] = Eq.fromUniversalEquals
}
