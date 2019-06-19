package com.swissborg.sbr.strategy.keepreferee

import cats.Id
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios._
import com.swissborg.sbr.strategy.Union
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected

class KeepRefereeSpec extends SBSpec {
  import KeepRefereeSpec._

  "KeepReferee" must {
    simulate[Id, KeepReferee, CleanPartitionsScenario]("handle clean partitions")(identity)

    simulate[Id, KeepReferee, UpDisseminationScenario]("handle split during up-dissemination")(
      identity
    )

    simulate[Id, KeepReferee, OldestRemovedDisseminationScenario](
      "handle a split during the oldest-removed scenarios"
    )(identity)

    simulate[Id, KeepRefereeWithIC, IndirectlyConnectedScenario]("handle non-clean partitions")(
      identity
    )

    simulate[Id, KeepRefereeWithIC, WithIndirectlyConnected[UpDisseminationScenario]](
      "handle non-clean partitions during up-dissemination scenarios"
    )(
      identity
    )

    simulate[Id, KeepRefereeWithIC, WithIndirectlyConnected[OldestRemovedDisseminationScenario]](
      "handle non-clean partitions during oldest-removed scenarios"
    )(identity)
  }
}

object KeepRefereeSpec {
  type KeepRefereeWithIC[F[_]] = Union[F, KeepReferee, IndirectlyConnected]
}
