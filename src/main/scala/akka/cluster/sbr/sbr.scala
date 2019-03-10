package akka.cluster

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

package object sbr {
  type QuorumSize = Int Refined Positive
}
