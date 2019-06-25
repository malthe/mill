package mill.contrib

import java.io.{BufferedReader, File, InputStreamReader}

import play.api.libs.json._
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.{CompletableFuture, Executors, Future}

import upickle.default._
import ch.epfl.scala.bsp4j.{BspConnectionDetails, BuildClient}
import mill._
import mill.contrib.bsp.ModuleUtils
import mill.define.{Discover, ExternalModule, Target, Task}
import mill.eval.Evaluator
import mill.scalalib._
import mill.util.DummyLogger
import org.eclipse.lsp4j.jsonrpc.{CompletableFutures, Launcher}
import requests.Compress.None
import upickle.default

import scala.collection.JavaConverters._


object MainMillBuildServer extends ExternalModule {

  implicit def millScoptEvaluatorReads[T] = new mill.main.EvaluatorScopt[T]()

  lazy val millDiscover: Discover[MainMillBuildServer.this.type] = Discover[this.type]
  val version = "1.0.0"
  val bspVersion = "2.0.0-M4"
  val languages = List("scala", "java")

  // returns the mill installation path in the user's system
  def whichMill(): String = {
    import sys.process._
    "which mill"!!
  }

  // creates a Json with the BSP connection details
  def createBspConnectionJson(): JsValue = {

    implicit val connectionWrites = new Writes[BspConnectionDetails] {
      def writes(connection: BspConnectionDetails) = Json.obj(
        "name" -> connection.getName,
        "argv" -> new JsArray(connection.getArgv.asScala.map(string => JsString(string)).toIndexedSeq),
        "version" -> connection.getVersion,
        "bspVersion" -> connection.getBspVersion,
        "languages" -> new JsArray(connection.getLanguages.asScala.map(string => JsString(string)).toIndexedSeq)
      )
    }
    val millPath = whichMill().replaceAll("\n", "")
    Json.toJson(new BspConnectionDetails("mill-bsp",
                                          List("java","-DMILL_CLASSPATH=" + millPath,
                                            "-DMILL_VERSION=0.4.0", "-Djna.nosys=true", "-cp",
                                            millPath,
                                            "mill.MillMain mill.contrib.MainMillBuildServer/startServer").asJava,
                                          version,
                                          bspVersion,
                                          languages.asJava))
  }

  /**
    * Installs the mill-bsp server. It creates a json file
    * with connection details in the ./.bsp directory for
    * a potential client to find.
    *
    * If a .bsp folder with a connection file already
    * exists in the working directory, it will be
    * overwritten and a corresponding message will be displayed
    * in stdout.
    *
    * If the creation of the .bsp folder fails due to any other
    * reason, the message and stacktrace of the exception will be
    * printed to stdout.
    *
    */
  def installMillBsp(): Unit = {
    val bspDirecotry = os.pwd / "contrib" / "bsp" / ".bsp"

    try {
      os.makeDir(bspDirecotry)
      os.write(bspDirecotry / "mill-bsp.json", Json.stringify(createBspConnectionJson()))
    } catch {
      case e: FileAlreadyExistsException => {
        println("The bsp connection json file probably exists already - will be overwritten")
        os.remove.all(bspDirecotry)
        installMillBsp()
      }
        //TODO: Do I want to catch this or throw the exception?
      case e: Exception => println("An exception occurred while installing mill-bsp: " + e.getMessage +
                                  " " + e.getStackTrace.toString)
    }

  }

  /**
    * Computes a mill task for resolving all JavaModules
    * defined in the build.sc file of the project to build.
    * This file should be in the working directory of the client.
    * @param ev: Environment, used by mill to evaluate tasks
    * @return: mill.Task which evaluates to a sequence of all
    *         the JavaModules defined for a project
    */
  def modules(ev: Evaluator): Task[Seq[JavaModule]] = T.task{
    ev.rootModule.millInternal.segmentsToModules.values.
      collect {
        case m: scalalib.JavaModule => m
      }.toSeq
  }

  /**
    * Computes a mill command which starts the mill-bsp
    * server and establishes connection to client. Waits
    * until a client connects and ends the connection
    * after the client sent an "exit" notification
    * @param ev Environment, used by mill to evaluate commands
    * @return: mill.Command which executes the starting of the
    *         server
    */
  def startServer(ev: Evaluator)  = T.command {

    val millServer = new mill.contrib.bsp.MillBuildServer(modules(ev)(), ev, bspVersion, version, languages)
    val executor = Executors.newCachedThreadPool()

    val stdin = System.in
    val stdout = System.out
    try {
      val launcher = new Launcher.Builder[BuildClient]()
        .setOutput(stdout)
        .setInput(stdin)
        .setLocalService(millServer)
        .setRemoteInterface(classOf[BuildClient])
        .setExecutorService(executor)
        .create()
      millServer.onConnectWithClient(launcher.getRemoteProxy)
      val listening = launcher.startListening()
      millServer.cancelator = () => listening.cancel(true)
      val voidFuture = listening.get()
    } catch {
      case e: Exception =>
        System.err.println("An exception occured while connecting to the client.")
        System.err.println(e.getMessage)
        e.printStackTrace()
    } finally {
      System.err.println("Shutting down executor")
      executor.shutdown()
    }
  }

  def experiment(ev: Evaluator) = T.command {
    val millServer = new mill.contrib.bsp.MillBuildServer(modules(ev)(), ev, bspVersion, version, languages)
    val mods: Seq[JavaModule] = modules(ev)()
    for (module <- mods) {
      System.err.println("Module: " + module + "has capabilities: " + ModuleUtils.getModuleCapabilities(module, ev))
      System.err.println("Base directory: " + module.millOuterCtx.millSourcePath)
      System.err.println("MIll source path: " + module.millSourcePath)
    }
  }

  /**
    * Allows minimal testing and installing the build server
    * from the command line.
    * @param args: can be  - exp: executes code in the experiment
    *                             method, helps with testing the server
    *                      - install: installs the mill-bsp server in the
    *                      working directory.
    */
  def main(args: Array[String]) {
    args(0) match {
      //case "exp" => experiment
      case "install" => installMillBsp() //TODO: Do I want to make this a mill command instead?
      case e: String => println("Wrong command, you can only use:\n   " +
                                "install - creates the bsp connection json file\n")
    }

  }
}

object foo extends mill.define.ExternalModule {

  implicit def millScoptEvaluatorReads[T] = new mill.main.EvaluatorScopt[T]()
  lazy val millDiscover: Discover[foo.this.type] = Discover[this.type]

  object bar extends ScalaModule {
    def scalaVersion = "2.12.4"
    override def ivyDeps = Agg(ivy"org.scalameta::metals:0.5.2")
    override def moduleDeps = Seq(baz)
    object test extends TestModule {
      override def ivyDeps = Agg(ivy"com.lihaoyi::utest:0.6.0")
      def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")
    }
  }

  object baz extends scalajslib.ScalaJSModule {
    def scalaJSVersion = "1.0.0-M8"
    def scalaVersion = "2.12.8"

  }

  object mill_exercise extends ScalaModule {
    def scalaVersion = "2.12.8"

    override def scalacPluginIvyDeps = Agg(ivy"org.scala-lang:scala-compiler:2.12.8")
    override def ivyDeps = Agg(
      ivy"org.scala-lang:scala-reflect:2.12.8",
      ivy"org.scalameta::metals:0.5.2"
    )

    object test extends Tests {
      override def ivyDeps = Agg(//ivy"org.scalameta::metals:0.5.2",
        ivy"com.lihaoyi::utest:0.6.0",
        ivy"org.scalactic::scalactic:3.0.5")

      def testFrameworks: Target[Seq[String]] = Seq("utest.runner.Framework")
    }
  }
}