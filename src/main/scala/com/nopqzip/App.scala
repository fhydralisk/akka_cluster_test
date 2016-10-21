package com.nopqzip
import akka.actor.{ActorSystem, Actor, Props, Terminated, Cancellable}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.ConfigFactory

/**
 * @author ${user.name}
 */

case class StMsg(text : String)
case class FindWatcher()
case class FindWatcherReply(found: Boolean)

class Watcher extends Actor {
  override def preStart = {
    println ("Watcher start")
  }
  
  override def postStop = {
    println("Watcher Stop")
  }
  
  def receive = {
    case StMsg(text) =>
      println(s"$text")
      // sender() ! StMsg("ok")
    
    case Terminated(a) =>
      print ("Watchee Terminated")
  }
}

class Watchee extends Actor {
  private var scheduler: Cancellable = null
  override def preStart = {
    val config = ConfigFactory.load()
    val host = config.getString("watch.watcher.tcp.hostname")
    val port = config.getInt("watch.watcher.tcp.port")
    println ("Watchee start")
    import context.dispatcher
    scheduler = context.system.scheduler.schedule(1 seconds, 1 seconds) {
      val actorSelection = context.actorSelection(s"akka.tcp://ClusterSystemName@$host:$port/user/watcher")
      println(actorSelection)
    }
  }
  
  override def postStop = {
    println ("Watchee stop")
  }
  
  def receive = {
    case StMsg(text) if text=="hi" => println("hi, watcher")
    case fw : FindWatcher =>
      println("finding watcher")
      sender ! FindWatcherReply(true)
    case _ => println("unhandle2")
  }
  
}

object WatcheeApp {
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watchee.tcp.port")
    val host = conf.getString("watch.watchee.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watcher]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    val watchee = system.actorOf(Props[Watchee], name="watchee") 
  }
}

object WatcherApp {
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watcher.tcp.port")
    val host = conf.getString("watch.watcher.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watcher]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    val watcher = system.actorOf(Props[Watcher], name="watcher")
    
    import system.dispatcher
    system.scheduler.schedule(2.seconds, 2.seconds) {
      implicit val timeout = Timeout(5.seconds)
      val reply = (watcher ? StMsg("hello"))
    }  
  }
}

object App {
  def main(args: Array[String]) = {
    WatcherApp.main(Array.empty)
  }
}
