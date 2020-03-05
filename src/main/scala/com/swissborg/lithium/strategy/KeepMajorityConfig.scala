package com.swissborg.lithium.strategy

import com.typesafe.config.Config

/**
 * [[KeepMajority]] configuration.
 *
 * @param role the role of the nodes to take in account.
 */
final case class KeepMajorityConfig(role: String)

object KeepMajorityConfig extends StrategyConfig[KeepMajorityConfig] {
  override val name: String = "keep-majority"

  override def fromConfig(config: Config): KeepMajorityConfig = {
    val prefix = s"com.swissborg.lithium.${name}"

    val role = config.getString(s"${prefix}.role")
    KeepMajorityConfig(role)
  }
}
