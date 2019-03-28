# George tooling

The project includes a comprehensive set of tools for for building and running the different artifacts that go into the final application.

This includes commands for building: 
- The jar-file - an "uberjar" containing all "dependencies" as well as the main code
- The properties-file describing certain aspects of the jar-file
- The JRE - a custom Java Runtime  
- The native installer which will install the above artifacts and more

This also includes commands for:
- Running the jar-file on the JRE or the standard Java
- Installing the jar-file in the appropriate location on your machine
- Organizing the artifacts into a site structure
- Running a web-server against said site structure

To get and overview of all custom commands, do:
```bash
lein george
```

From their you can explore the different commands (a.k.a. "tasks") by doing:

```bash
lein help <task>
```

and:
```bash
lein help <task> <subtask>
```

[TOC]

{{TOC}}


## Building stuff

The 'build' task outputs its different artifacts in the 'target' directory as
```
target/<platform>/<artifact>[/**]
```
and
```
target/Site/**
```
'platform' is one of Windows, MacOS, or other.
'artifact' is one of jar, jre, installer.

Again, for details do:
```bash
lein help build
```


## Run stuff

Running something assumes you have done the appropriate builds first.

### The custom JRE

To run anything using `java` in the custom runtime, you can do:
```bash
lein jre [args]
```
Try doing:
```bash
lein jre --list-modules
```

If you do:
```bash
lein java --list-modules
```
... you will se that the modules list is much longer for `java` than for `jre java`.


### The jar

To run the jar on the custom JRE, do:
```bash
lein jre :jar
```

To run the jar on standard java, do:
```bash
lein java :jar
```


### In Leiningen

To run the project in Leiningen, do:
```bash
lein run
```

This simply runs the code in Leiningen using the standard java.

_It is not possible to run Leiningen using the custom jre, as Leiningen requires a JDK JRE:_

> Java compiler not found; Be sure to use java from a JDK
  rather than a JRE by modifying PATH or setting JAVA_CMD.

  
### In a repl

When developing you will want to use standard repl development mechanims.  There are currently no special repl tools integrated in George this.  But by un-commenting one of the lines at the bottom of `george.application.launcher`, a new instance of George is launched in the repl whenever you (re-)load that module. 
