package com.swissborg.sbr.strategy.keepoldest

import cats.implicits._
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios.{
  CleanPartitionsScenario,
  IndirectlyConnectedScenario,
  UpDisseminationScenario,
  WithIndirectlyConnected
}
import com.swissborg.sbr.strategy.Union
import com.swissborg.sbr.strategy.indirectlyconnected.IndirectlyConnected

import scala.util.Try

class KeepOldestSpec extends SBSpec {
  import KeepOldestSpec._

  "KeepOldest" must {
    simulate[Try, KeepOldest, CleanPartitionsScenario]("handle clean partitions")(_.get)

    simulate[Try, KeepOldest, UpDisseminationScenario]("handle split during up-dissemination")(
      _.get
    )

    simulate[Try, KeepOldestWithIC, IndirectlyConnectedScenario]("handle non-clean partitions")(
      _.get
    )

    simulate[Try, KeepOldestWithIC, WithIndirectlyConnected[UpDisseminationScenario]](
      "handle non-clean partitions during up-dissemination"
    )(_.get)
  }
}

object KeepOldestSpec {
  type KeepOldestWithIC[F[_]] = Union[F, KeepOldest, IndirectlyConnected]
}
