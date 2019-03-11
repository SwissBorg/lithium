package akka.cluster.sbr.strategies

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

package object staticquorum {
  type QuorumSize = Int Refined Positive
}
