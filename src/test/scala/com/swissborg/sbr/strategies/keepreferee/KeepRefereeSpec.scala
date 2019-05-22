package com.swissborg.sbr.strategies.keepreferee

import cats.Id
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.scenarios._

class KeepRefereeSpec extends SBSpec {
  "KeepReferee" must {
    simulate[Id, KeepReferee, SymmetricSplitScenario]("handle symmetric split scenarios")(identity)

    simulate[Id, KeepReferee, UpDisseminationScenario]("handle split during up-dissemination")(identity)

    simulate[Id, KeepReferee, OldestRemovedScenario]("handle a split during the oldest-removed scenarios")(identity)
  }
}
