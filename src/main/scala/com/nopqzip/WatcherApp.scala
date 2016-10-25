package com.nopqzip

import akka.actor.{ActorSystem, Actor, ActorRef, Props, Terminated}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class Watcher extends Actor {
  override def preStart = {
    println ("Watcher start")
  }
  
  override def postStop = {
    println("Watcher Stop")
  }
  
  def receive = {
    case StMsg(text) if text equals "hello" =>
      sender() ! StMsg("hi")
      val watchee = sender()
      import context.dispatcher
      context.system.scheduler.scheduleOnce(15 seconds) {
        println("15s left to watch")
      }
      context.system.scheduler.scheduleOnce(30 seconds) {
        println("watching watchee")
        context watch watchee
      }
      
    case StMsg(text) =>
      println("Receive " + text + " from " + sender.toString())
      // sender() ! StMsg("ok")
    
    case Terminated(a) =>
      print ("Watchee Terminated, sender is " + sender.toString())
  }
}

object WatcherApp {
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watcher.tcp.port")
    val host = conf.getString("watch.watcher.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watcher]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    val watcher = system.actorOf(Props[Watcher], name="watcher")
  }
}
