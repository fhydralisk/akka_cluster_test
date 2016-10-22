package com.nopqzip
import akka.actor.{ActorSystem, Actor, ActorRef, Props, Terminated, Cancellable}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.ConfigFactory

/**
 * @author ${user.name}
 */

case class StMsg(text : String)

class Watcher extends Actor {
  override def preStart = {
    println ("Watcher start")
  }
  
  override def postStop = {
    println("Watcher Stop")
  }
  
  def receive = {
    case StMsg(text) if text == "hello" =>
      sender() ! StMsg("hi")
      import context.dispatcher
      val senderActor : ActorRef = sender()
      context.system.scheduler.scheduleOnce(30.seconds) {
        println("10 seconds left to watch")
      }
      context.system.scheduler.scheduleOnce(40.seconds) {
        println("watching sender" + sender.toString())
        context watch senderActor
      }
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
      val actorSelection = context.actorSelection(s"akka.tcp://ClusterSystem@$host:$port/user/watcher")
      println(actorSelection)
      // implicit val timeout = Timeout(5.seconds)
      actorSelection ! StMsg("hello")
    }
  }
  
  override def postStop = {
    println ("Watchee stop")
  }
  
  def receive = {
    case StMsg(text) if text=="hi" => 
      println("hi, watcher")
      if (scheduler != null) { 
        scheduler.cancel() 
        scheduler = null
      }
    case _ => println("unhandle2")
  }
  
}

object WatcheeApp {
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watchee.tcp.port")
    val host = conf.getString("watch.watchee.tcp.hostname")
    val terminate = conf.getInt("watch.watchee.testing.terminate")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watchee]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    val watchee = system.actorOf(Props[Watchee], name="watchee") 
    if (terminate > 0) {
      import system.dispatcher
      system.scheduler.scheduleOnce (terminate.seconds) {
        println("terminating actor system")
        system.terminate().onSuccess {
          case result =>
            println(result)
            println("actor system down")
        }
      }
    }
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

object App {
  def main(args: Array[String]) = {
    WatcheeApp.main(Array.empty)
    WatcherApp.main(Array.empty)
  }
}
