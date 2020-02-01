package com.swissborg.lithium.strategy

import com.typesafe.config.Config

/**
 * [[KeepOldest]] configuration.
 *
 * @param downIfAlone down the oldest node if it is cutoff from all the nodes with the given role.
 * @param role        the role of the nodes to take in account.
 */
final case class KeepOldestConfig(downIfAlone: Boolean, role: String)

object KeepOldestConfig extends StrategyConfig[KeepOldestConfig] {
  override val name: String = "keep-oldest"

  override def fromConfig(config: Config): KeepOldestConfig = {
    val prefix = s"com.swissborg.lithium.${name}"

    val downIfAlone = config.getBoolean(s"${prefix}.down-if-alone")
    val role        = config.getString(s"${prefix}.role")
    KeepOldestConfig(downIfAlone, role)
  }
}
