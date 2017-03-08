package com.nopqzip

import akka.actor.ActorSystem
import java.net.NetworkInterface
import java.net.InetAddress

import com.typesafe.config.ConfigFactory

/**
 * @author ${user.name}
 */


object App {
  def main(args: Array[String]) = {
    val hostnameAddrs = Set("odl1.nopqzip.com", "odl2.nopqzip.com", "odl3.nopqzip.com") map {
      e => InetAddress.getByName(e) -> e
    } toMap

    val addresses = NetworkInterface.getByName("eth0").getInetAddresses()
    import scala.collection.JavaConverters._
    val hostnameAddr = addresses.asScala.filter( _.getAddress().length == 4).next()
    val hostname = hostnameAddrs.filterKeys( _ == hostnameAddr).head._2
    val config = ConfigFactory.parseString(s"""akka.remote.netty.tcp.hostname = "$hostname"""").withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterShard", config)

  }
}
