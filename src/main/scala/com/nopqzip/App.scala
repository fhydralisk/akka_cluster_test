package com.nopqzip

import akka.actor.ActorSystem
import java.net.NetworkInterface

import com.typesafe.config.ConfigFactory

/**
 * @author ${user.name}
 */


object App {
  def main(args: Array[String]) = {
    val hostname = NetworkInterface.getByName("eth0").getInetAddresses().nextElement().getHostName()
    val config = ConfigFactory.parseString(s"""akka.remote.netty.tcp.hostname = "$hostname"""").withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterShard", config)

  }
}
