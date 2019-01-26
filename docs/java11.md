# Java 11


George now required Java 11.  
The main reasons for moving from Java 8 to 11:
1. Being able to build a deployable custom runtime.
2. Accessing new functionality in Java 11.

Also, we are now switching to OpenJDK due mainly to changed licencing.

## Java 11 JDK installation

Download and install the latest version of OpenJDK 11 from [jdk.java.net](https://jdk.java.net/11)
You do not need to use Java 11 as your default Java, but can have it installed and available in parallel with other versions.  See the "JAVA_HOME" section bellow.
 

### JAVA_HOME

Both Leiningen and the project depends on the system variable `JAVA_HOME`. It should point to your Java 11 JDK Home directory.  
On my machine (MacOS), I do:
```bash
echo $JAVA_HOME
```
And get:
> /Library/Java/JavaVirtualMachines/jdk-11.0.1.jdk/Contents/Home


I have put a couple of aliases in my `.bashrc` which allow me to toggle easily between different versions:
```bash
alias set-java8="export JAVA_HOME=<java8home>"
alias set-java11="export JAVA_HOME=<java11home>"
```

_Check your version_ by doing:
```bash
lein -version
```
I get: 
> Leiningen 2.8.1 on Java 11.0.1 OpenJDK 64-Bit Server VM


## Java Platform Module System

JMS a.k.a. Jigsaw was introduced with Java 9.  
[Article on Wikipedia](https://en.wikipedia.org/wiki/Java_Platform_Module_System)


However, we currently built and run George in "legacy" mode.  There is currently no good reason to strive for "jms" mode.


## Build

Builds are done using a mix of aliases, profiles and plugins.

The jar and the runtime are both built with a custom task.  For details, do:
```bash
lein help build
```

This results in a OS-specific jar-file without module-info.class.  When run - either on standard java runtime or custom runtime, it will be run in "legacy" mode. 

Also, you will get an OS-specific custom runtime in the directory `<project_root>/target/jre` .  
It will include only the modules listed in the project-files `[:module :jre]`.


## Run

A mix of aliases et al allow you to run what you have build.

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

