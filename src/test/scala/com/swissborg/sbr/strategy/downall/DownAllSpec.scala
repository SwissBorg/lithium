package com.swissborg.sbr.strategy.downall

import cats.Id
import cats.implicits._
import com.swissborg.sbr._
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.StrategyDecision._
import com.swissborg.sbr.strategy.Union
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected

class DownAllSpec extends SBSpec {
  import DownAllSpec._

  "DownAll" must {
    "always down nodes" in {
      val downAll: DownAll[Id] = new DownAll[Id]
      forAll { worldView: WorldView =>
        downAll.takeDecision(worldView).map {
          case _: DownReachable => succeed
          case _                => fail
        }
      }
    }

    simulate[Id, DownAll, CleanPartitionsScenario]("handle clean partitions")(identity)

    simulate[Id, DownAll, UpDisseminationScenario](
      "handle a split during up-dissemination scenarios"
    )(identity)

    simulate[Id, DownAll, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(identity)

    simulate[Id, DownAllWithIC, IndirectlyConnectedScenario]("handle non-clean partitions")(
      identity
    )

    simulate[Id, DownAllWithIC, WithIndirectlyConnected[UpDisseminationScenario]](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      identity
    )

    simulate[Id, DownAllWithIC, WithIndirectlyConnected[OldestRemovedDisseminationScenario]](
      "handle non-clean partitions during oldest-removed scenarios"
    )(
      identity
    )
  }
}

object DownAllSpec {
  // TODO not needed it already downs everything.
  type DownAllWithIC[F[_]] = Union[F, DownAll, IndirectlyConnected]
}
