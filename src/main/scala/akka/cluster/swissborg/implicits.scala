package akka.cluster.swissborg

import akka.cluster.Reachability.ReachabilityStatus
import cats.Eq

object implicits extends com.swissborg.sbr.implicits {
  implicit val reachabilityStatusEq: Eq[ReachabilityStatus] = Eq.fromUniversalEquals
}
