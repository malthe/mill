
The plugins in this section are developed/maintained in the mill git tree.

When using one of these, it is important that the versions you load match your mill version. To facilitate this, Mill will automatically replace the `$MILL_VERSION` literal in your ivy imports with the correct value.

For instance :

```scala
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
```

[comment]: # (Please keep list of plugins in alphabetical order)

## Bloop

This plugin generates [bloop](https://scalacenter.github.io/bloop/) configuration
from your build file, which lets you use the bloop CLI for compiling, and makes
your scala code editable in [Metals](https://scalameta.org/metals/)


### Quickstart
```scala
// build.sc (or any other .sc file it depends on, including predef)
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
```

Then in your terminal :

```
> mill mill.contrib.Bloop/install
```

It generate correct bloop config for any `JavaModule`, `ScalaModule`,
`ScalaJsModule` or `ScalaNativeModule` under the `.bloop` folder

### Mix-in

You can mix-in the `Bloop.Module` trait with any JavaModule to quickly access
the deserialised configuration for that particular module:

```scala
// build.sc
import mill._
import mill.scalalib._
import mill.contrib.Bloop

object MyModule extends ScalaModule with Bloop.Module {
  def myTask = T { bloop.config() }
}
```

### Note regarding metals

Generating the bloop config should be enough for metals to pick it up and for
features to start working in vscode (or the bunch of other editors metals supports).
However, note that this applies only to your project sources. Your mill/ammonite related
`.sc` files are not yet supported by metals.

The generated bloop config references the semanticDB compiler plugin required by
metals to function. If need be, the version of semanticDB can be overriden by
extending `mill.contrib.bloop.BloopImpl` in your own space.

### Note regarding current mill support in bloop

The mill-bloop integration currently present in the [bloop codebase](https://github.com/scalacenter/bloop/blob/master/integrations/mill-bloop/src/main/scala/bloop/integrations/mill/MillBloop.scala#L10)
will be deprecated in favour of this implementation.


## BuildInfo

Generate scala code from your buildfile.
This plugin generates a single object containing information from your build.

To declare a module that uses BuildInfo you must extend the `mill.contrib.buildinfo.BuildInfo` trait when defining your module.

Quickstart:
```scala
// build.sc
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import mill.contrib.buildinfo.BuildInfo

object project extends BuildInfo {
  val name = "poject-name"
  def  buildInfoMembers: T[Map[String, String]] = T {
    Map(
      "name" -> name),
      "scalaVersion" -> scalaVersion()
    )
  }
}
```

### Configuration options

* `def buildInfoMembers: T[Map[String, String]]`
  The map containing all member names and values for the generated info object.

* `def buildInfoObjectName: String`, default: `BuildInfo`
  The name of the object which contains all the members from `buildInfoMembers`.

* `def buildInfoPackageName: Option[String]`, default: `None`
  The package name of the object.

## Docker

Automatically build docker images from your mill project.

Requires the docker CLI to be installed.

In the simplest configuration just extend `DockerModule` and declare a `DockerConfig` object.

```scala
import mill._, scalalib._

import $ivy.`com.lihaoyi::mill-contrib-docker:$MILL_VERSION`
import contrib.docker.DockerModule

object foo extends JavaModule with DockerModule {
  object docker extends DockerConfig
}
```

Then

```
$ mill foo.docker.build
$ docker run foo
```

### Configuration

Configure the image by overriding tasks in the `DockerConfig` object

```scala
object docker extends DockerConfig {
  // Override tags to set the output image name
  def tags = List("aws_account_id.dkr.ecr.region.amazonaws.com/hello-repository")

  def baseImage = "openjdk:11"

  // Configure whether the docker build should check the remote registry for a new version of the base image before building.
  // By default this is true if the base image is using a latest tag
  def pullBaseImage = true
}
```

Run mill in interactive mode to see the docker client output, like `mill -i foo.docker.build`.

## Flyway

Enables you to configure and run [Flyway](https://flywaydb.org/) commands from your mill build file.
The flyway module currently supports the most common flyway use cases with file based migrations.

Configure flyway by overriding settings in your module. For example

```scala
// build.sc

import mill._, scalalib._

import $ivy.`com.lihaoyi::mill-contrib-flyway:$MILL_VERSION`
import contrib.flyway.FlywayModule

object foo extends ScalaModule with FlywayModule {
  def scalaVersion = "2.12.8"

  //region flyway
  def flywayUrl = "jdbc:postgresql:myDb" // required
  def flywayDriverDeps = Agg(ivy"org.postgresql:postgresql:42.2.5") // required
  def flywayUser = "postgres" // optional
  // def flywayPassword = "" // optional
  //endregion
}
```

Flyway will look for migration files in `db/migration` in all resources folders by default.
This should work regardless of if you are using a mill or sbt project layout.

You can then run common flyway commands like

```
mill foo.flywayClean
mill foo.flywayInfo
mill foo.flywayMigrate
```

> REMINDER:
> You should never hard-code credentials or check them into a version control system.
> You should write some code to populate the settings for flyway instead.
> For example `def flywayPassword = T.input(T.ctx().env("FLYWAY_PASSWORD"))`

## Play Framework

This module adds basic Play Framework support to mill:

* configures mill for Play default directory layout,
* integrates the Play routes compiler,
* provides helpers for commonly used framework libraries,
* optionally: integrates the Twirl template engine,
* optionally: configures mill for single module play applications.

There is no specific Play Java support, building a Play Java application will require a bit
of customization (mostly adding the proper dependencies).

### Using the plugin

There are 2 base modules and 2 helper traits in this plugin, all of which can be found
 in `mill.playlib`.

The base modules:

* `PlayModule` applies the default Play configuration (layout, dependencies, routes compilation,
Twirl compilation and Akka HTTP server)
* `PlayApiModule` applies the default Play configuration without `Twirl` templating. This is useful
if your Play app is a pure API server or if you want to use a different templating engine.

The two helper traits:

* `SingleModule` can be useful to configure mill for a single module Play application such as the
[play-scala-seed project](https://github.com/playframework/play-scala-seed.g8). Mill is
multi-module by default and requires a bit more configuration to have source, resource, and test
directories at the top level alongside the `build.sc` file. This trait takes care of that (See
[Using SingleModule](#using-singlemodule) below).
* `RouterModule` allows you to use the Play router without the rest of the configuration (see
[Using the router module directly](#using-the-router-module-directly).)

### Using `PlayModule`

In order to use the `PlayModule` for your application, you need to provide the scala, Play and
Twirl versions. You also need to define your own test object which extends the provided
`PlayTests` trait.

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object core extends PlayModule {
    //config
    override def scalaVersion= T{"2.12.8"}
    override def playVersion= T{"2.7.0"}
    override def twirlVersion= T{"1.4.0"}

    object test extends PlayTests
}
```

Using the above definition, your build will be configured to use the default Play layout:

```text
.
├── build.sc
└── core
    ├── app
    │   ├── controllers
    │   └── views
    ├── conf
    │   └── application.conf
    │   └── routes
    │   └── ...
    ├── logs
    ├── public
    │   ├── images
    │   ├── javascripts
    │   └── stylesheets
    └── test
        └── controllers
```

The following compile dependencies will automatically be added to your build:

```
ivy"com.typesafe.play::play:${playVersion()}",
ivy"com.typesafe.play::play-guice:${playVersion()}",
ivy"com.typesafe.play::play-server:${playVersion()}",
ivy"com.typesafe.play::play-logback:${playVersion()}"
```

Scala test will be setup as the default test framework and the following test dependencies will be
added (the actual version depends on the version of Play you are pulling `2.6.x` or `2.7.x`):

```
ivy"org.scalatestplus.play::scalatestplus-play::4.0.1"
```

In order to have a working `start` command the following runtime dependency is also added:

```
ivy"com.typesafe.play::play-akka-http-server:${playVersion()}"
```

### Using `PlayApiModule`

The `PlayApiModule` trait behaves the same as the `PlayModule` trait but it won't process .scala
.html files and you don't need to define the `twirlVersion:

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object core extends PlayApiModule {
    //config
    override def scalaVersion= T{"2.12.8"}
    override def playVersion= T{"2.7.0"}

    object test extends PlayTests
}
```

### Play configuration options

The Play modules themselves don't have specific configuration options at this point but the [router
module configuration options](#router-configuration-options) and the [Twirl module configuration options](#twirl-configuration-options) are applicable.

### Additional play libraries

The following helpers are available to provide additional Play Framework dependencies:

* `core()` - added by default ,
* `guice()` - added by default,
* `server()` - added by default,
* `logback()` - added by default,
* `evolutions()` - optional,
* `jdbc()` - optional,
* `filters()` - optional,
* `ws()` - optional,
* `caffeine()` - optional.

If you want to add an optional library using the helper you can do so by overriding `ivyDeps`
like in the following example build:

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object core extends PlayApiModule {
    //config
    override def scalaVersion= T{"2.12.8"}
    override def playVersion= T{"2.7.0"}

    object test extends PlayTests

    override def ivyDeps = T{ super.ivyDeps() ++ Agg(ws(), filters()) }
}
```

### Commands equivalence

Mill commands are targets on a named build. For example if your build is called `core`:

* compile: `core.compile`
* run: *NOT Implemented yet*. It can be approximated with `mill -w core.runBackground` but this
starts a server in *PROD* mode which:
  * doesn't do any kind of classloading magic (meaning potentially slower restarts)
  * returns less detailed error messages (no source code extract and line numbers)
  * can sometimes fail because of a leftover RUNNING_PID file
* start: `core.start` or `core.run` both start the server in *PROD* mode.
* test: `core.test`
* dist: *NOT Implemented yet*. However you can use the equivalent `core.assembly`
command to get a runnable fat jar of the project. The packaging is slightly different but should
be find for a production deployment.

### Using `SingleModule`

The `SingleModule` trait allows you to have the build descriptor at the same level as the source
 code on the filesystem. You can move from there to a multi-module build either by refactoring
 your directory layout into multiple subdirectories or by using mill's nested modules feature.

Looking back at the sample build definition in [Using PlayModule](#using-playmodule):

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object core extends PlayModule {
    //config
    override def scalaVersion= T{"2.12.8"}
    override def playVersion= T{"2.7.0"}
    override def twirlVersion= T{"1.4.0"}

    object test extends PlayTests
}
```

The directory layout was:

```text
.
├── build.sc
└── core
    ├── app
    │   ├── controllers
    │   └── views
    ├── conf
    │   └── application.conf
    │   └── routes
    │   └── ...
    ├── logs
    ├── public
    │   ├── images
    │   ├── javascripts
    │   └── stylesheets
    └── test
        └── controllers
```

by mixing in the `SingleModule` trait in your build:

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object core extends PlayModule with SingleModule {
	//config
	override def scalaVersion= T{"2.12.8"}
	override def playVersion= T{"2.7.0"}
	override def twirlVersion= T{"1.4.0"}

	object test extends PlayTests
}
```

the layout becomes:

```text
.
└── core
    ├── build.sc
    ├── app
    │   ├── controllers
    │   └── views
    ├── conf
    │   └── application.conf
    │   └── routes
    │   └── ...
    ├── logs
    ├── public
    │   ├── images
    │   ├── javascripts
    │   └── stylesheets
    └── test
        └── controllers
```

#### Using the router module directly

If you want to use the router module in a project which doesn't use the default Play layout, you
can mix-in the `mill.playlib.routesModule` trait directly when defining your module. Your app must
define `playVersion` and `scalaVersion`.

```scala
// build.sc
import mill._
import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._


object app extends ScalaModule with RouterModule {
  def playVersion= T{"2.7.0"}
  def scalaVersion= T{"2.12.8"}
}
```

##### Router Configuration options

* `def playVersion: T[String]` (mandatory) - The version of Play to use to compile the routes file.
* `def scalaVersion: T[String]` - The scalaVersion in use in your project.
* `def routes: Sources` - The directory which contains your route files. (Defaults to : `routes/`)
* `def routesAdditionalImport: Seq[String]` - Additional imports to use in the generated routers.
  (Defaults to `Seq("controllers.Assets.Asset", "play.libs.F")`
* `def generateForwardsRouter: Boolean = true` - Enables the forward router generation.
* `def generateReverseRouter: Boolean = true` - Enables the reverse router generation.
* `def namespaceReverseRouter: Boolean = false` - Enables the namespacing of reverse routers.
* `def generatorType: RouteCompilerType = RouteCompilerType.InjectedGenerator` - The routes
  compiler type, one of RouteCompilerType.InjectedGenerator or RouteCompilerType.StaticGenerator

##### Details

The following filesystem layout is expected by default:

```text
.
├── app
│   └── routes
│       └── routes
└── build.sc
```

`RouterModule` adds the `compileRouter` task to the module:

```
mill app.compileRouter
```

(it will be automatically run whenever you compile your module)

This task will compile `routes` templates into the `out/app/compileRouter/dest`
directory. This directory must be added to the generated sources of the module to be compiled and
made accessible from the rest of the code. This is done by default in the trait, but if you need
to have a custom override for `generatedSources` you can get the list of files from `routerClasses`

To add additional imports to all of the routes:

```scala
// build.sc
import mill.scalalib._

import $ivy.`com.lihaoyi::mill-contrib-playlib:$MILL_VERSION`,  mill.playlib._

object app extends ScalaModule with RouterModule {
  def playVersion = "2.7.0"
  override def routesAdditionalImport = Seq("my.additional.stuff._", "my.other.stuff._")
}
```


## ScalaPB

This module allows [ScalaPB](https://scalapb.github.io) to be used in Mill builds. ScalaPB is a [Protocol Buffers](https://developers.google.com/protocol-buffers/) compiler plugin that generates Scala case classes, encoders and decoders for protobuf messages.

To declare a module that uses ScalaPB you can extend the `mill.contrib.scalapblib.ScalaPBModule` trait when defining your module.

This creates a Scala module which compiles `.proto` files in the `protobuf` folder of the module with ScalaPB and adds the resulting `.scala` sources to your module's `generatedSources`.

```scala
// build.sc

import $ivy.`com.lihaoyi::mill-contrib-scalapblib:$MILL_VERSION`
import contrib.scalapblib._

object example extends ScalaPBModule {
  def scalaVersion = "2.12.6"
  def scalaPBVersion = "0.7.4"
}
```

This defines a project with the following layout:

```
build.sc
example/
    src/
    protobuf/
    resources/
```

### Configuration options

* scalaPBVersion (mandatory) - The ScalaPB version `String` e.g. `"0.7.4"`

* scalaPBFlatPackage - A `Boolean` option which determines whether the `.proto` file name should be appended as the final segment of the package name in the generated sources.

* scalaPBJavaConversions - A `Boolean` option which determines whether methods for converting between the generated Scala classes and the Protocol Buffers Java API classes should be generated.

* scalaPBGrpc - A `Boolean` option which determines whether [grpc](https://grpc.io) stubs should be generated.

* scalaPBSingleLineToProtoString - A `Boolean` option which determines whether the generated `.toString` methods should use a single line format.

* scalaPBProtocPath - A `Option[Path]` option which determines the protoc compiler to use. If `None`, a java embedded protoc will be used, if set to `Some` path, the given binary is used.

If you'd like to configure the options that are passed to the ScalaPB compiler directly, you can override the `scalaPBOptions` task, for example:

```scala
// build.sc

import $ivy.`com.lihaoyi::mill-contrib-scalapblib:$MILL_VERSION`
import contrib.scalapblib._

object example extends ScalaPBModule {
  def scalaVersion = "2.12.6"
  def scalaPBVersion = "0.7.4"
  override def scalaPBOptions = "flat_package,java_conversions"
}
```


## Scoverage

This module allows you to generate code coverage reports for Scala projects with
[Scoverage](https://github.com/scoverage) via the
[scalac-scoverage-plugin](https://github.com/scoverage/scalac-scoverage-plugin).

To declare a module for which you want to generate coverage reports you can
extends the `mill.contrib.scoverage.ScoverageModule` trait when defining your
module. Additionally, you must define a submodule that extends the
`ScoverageTests` trait that belongs to your instance of `ScoverageModule`.

```scala
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import mill.contrib.scoverage.ScoverageModule

object foo extends ScoverageModule  {
  def scalaVersion = "2.12.9"
  def scoverageVersion = "1.4.0"

  object test extends ScoverageTests {
    def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.0.8")
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
```

In addition to the normal tasks available to your Scala module, Scoverage
modules introduce a few new tasks and changes the behavior of an existing one.

```
mill foo.scoverage.compile      # compiles your module with test instrumentation
                                # (you don't have to run this manually, running the test task will force its invocation)

mill foo.test                   # tests your project and collects metrics on code coverage
mill foo.scoverage.htmlReport   # uses the metrics collected by a previous test run to generate a coverage report in html format
mill foo.scoverage.xmlReport    # uses the metrics collected by a previous test run to generate a coverage report in xml format
```

The measurement data is available at `out/foo/scoverage/data/`,
the html report is saved in `out/foo/scoverage/htmlReport/`,
and the xml report is saved in `out/foo/scoverage/xmlReport/`.


## TestNG

Provides support for [TestNG](https://testng.org/doc/index.html).

To use TestNG as test framework, you need to add it to the `TestModule.testFrameworks` property.

```scala
// build.sc
import mill.scalalib._

object project extends ScalaModule {
  object test extends Tests{
    def testFrameworks = Seq("mill.testng.TestNGFramework")
  }
}
```

## Tut

This module allows [Tut](https://tpolecat.github.io/tut) to be used in Mill builds. Tut is a documentation tool which compiles and evaluates Scala code in documentation files and provides various options for configuring how the results will be displayed in the compiled documentation.

To declare a module that uses Tut you can extend the `mill.contrib.tut.TutModule` trait when defining your module.

This creates a Scala module which compiles markdown, HTML and `.txt` files in the `tut` folder of the module with Tut.

By default the resulting documents are simply placed in the Mill build output folder but they can be placed elsewhere by overriding the `tutTargetDirectory` task.

```scala
// build.sc

import $ivy.`com.lihaoyi::mill-contrib-tut:$MILL_VERSION`
import contrib.tut._

object example extends TutModule {
  def scalaVersion = "2.12.6"
  def tutVersion = "0.6.7"
}
```

This defines a project with the following layout:

```
build.sc
example/
    src/
    tut/
    resources/
```

In order to compile documentation we can execute the `tut` task in the module:

```
sh> mill example.tut
```

### Configuration options

* tutSourceDirectory - This task determines where documentation files must be placed in order to be compiled with Tut. By default this is the `tut` folder at the root of the module.

* tutTargetDirectory - A task which determines where the compiled documentation files will be placed. By default this is simply the Mill build's output folder for the `tutTargetDirectory` task but this can be reconfigured so that documentation goes to the root of the module (e.g. `millSourcePath`) or to a dedicated folder (e.g. `millSourcePath / 'docs`)

* tutClasspath - A task which determines what classpath is used when compiling documentation. By default this is configured to use the same inputs as the `runClasspath`, except for using `tutIvyDeps` rather than the module's `ivyDeps`.

* tutScalacPluginIvyDeps - A task which determines the scalac plugins which will be used when compiling code examples with Tut. The default is to use the `scalacPluginIvyDeps` for the module.

* tutNameFilter - A `scala.util.matching.Regex` task which will be used to determine which files should be compiled with tut. The default pattern is as follows: `.*\.(md|markdown|txt|htm|html)`.

* tutScalacOptions - The scalac options which will be used when compiling code examples with Tut. The default is to use the `scalacOptions` for the module but filtering out options which are problematic in the REPL, e.g. `-Xfatal-warnings`, `-Ywarn-unused-imports`.

* tutVersion - The version of Tut to use.

* tutIvyDeps - A task which determines how to fetch the Tut jar file and all of the dependencies required to compile documentation for the module and returns the resulting files.

* tutPluginJars - A task which performs the dependency resolution for the scalac plugins to be used with Tut.

## Twirl

Twirl templates support.

To declare a module that needs to compile twirl templates you must extend the `mill.twirllib.TwirlModule` trait when defining your module.
Also note that twirl templates get compiled into scala code, so you also need to extend `ScalaModule`.

```scala
// build.sc
import mill.scalalib._

import $ivy.`com.lihaoyi::mill-contrib-twirllib:$MILL_VERSION`,  mill.twirllib._

object app extends ScalaModule with TwirlModule {
// ...
}
```

### Twirl configuration options

* `def twirlVersion: T[String]` (mandatory) - the version of the twirl compiler to use, like "1.3.15"
* `def twirlAdditionalImports: Seq[String] = Nil` - the additional imports that will be added by twirl compiler to the top of all templates
* `def twirlConstructorAnnotations: Seq[String] = Nil` - annotations added to the generated classes' constructors (note it only applies to templates with `@this(...)` constructors)
* `def twirlCodec = Codec(Properties.sourceEncoding)` - the codec used to generate the files (the default is the same sbt plugin uses)
* `def twirlInclusiveDot: Boolean = false`

### Details

The following filesystem layout is expected:

```text
build.sc
app/
    views/
        view1.scala.html
        view2.scala.html
```

`TwirlModule` adds the `compileTwirl` task to the module:

```
mill app.compileTwirl
```

(it will be automatically run whenever you compile your module)

This task will compile `*.scala.html` templates (and others, like `*.scala.txt`) into the `out/app/compileTwirl/dest`
directory. This directory must be added to the generated sources of the module to be compiled and made accessible from the rest of the code:

```scala
// build.sc
import mill.scalalib._

import $ivy.`com.lihaoyi::mill-contrib-twirllib:$MILL_VERSION`,  mill.twirllib._

object app extends ScalaModule with TwirlModule {
  def twirlVersion = "1.3.15"
  def generatedSources = T{ Seq(compileTwirl().classes) }
}
```

To add additional imports to all of the twirl templates:

```scala
// build.sc
import mill.scalalib._

import $ivy.`com.lihaoyi::mill-contrib-twirllib:$MILL_VERSION`,  mill.twirllib._

object app extends ScalaModule with TwirlModule {
  def twirlVersion = "1.3.15"
  override def twirlAdditionalImports = Seq("my.additional.stuff._", "my.other.stuff._")
  def generatedSources = T{ Seq(compileTwirl().classes) }
}
```

as the result all templates will get this line at the top:

```scala
@import "my.additional.stuff._"
@import "my.other.stuff._"
```

Besides that, twirl compiler has default imports, at the moment these:

```scala
Seq(
    "_root_.play.twirl.api.TwirlFeatureImports._",
    "_root_.play.twirl.api.TwirlHelperImports._",
    "_root_.play.twirl.api.Html",
    "_root_.play.twirl.api.JavaScript",
    "_root_.play.twirl.api.Txt",
    "_root_.play.twirl.api.Xml"
)
```

These imports will always be added to every template.  You don't need to list them if you override `twirlAdditionalImports`.

### Example
There's an [example project](https://github.com/lihaoyi/cask/tree/master/example/twirl)
