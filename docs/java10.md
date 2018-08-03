# Java 10


George now required Java 10.  
The main reasons for moving from Java 8 to 10:
1. Being able to build a deployable custom runtime.
2. Accessing new functionality in Java 10.


## Java 10 JDK installation

Download and install the latest version of Oracle's Java 10 JDK from [Java SE Downloads](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

You do not need to use Java 10 as your default Java, but can have it installed and available in parallel with other versions.  See the "JAVA_HOME" section bellow.
 
_**OpenJDK** installations usually don't include JavaFX modules by default._ If you prefer OpenJDK, you may need to go through a few extra steps to handle issue. 
(Are you a Linux user who has had to deal with this? Please contribute with instructions here.)

### JAVA_HOME

Both Leiningen and the project depends on the system variable `JAVA_HOME`. It should point to your Java 10 JDK Home directory.  
On my machine (MacOS), I do:
```bash
echo $JAVA_HOME
```
And get:
> /Library/Java/JavaVirtualMachines/jdk-10.0.2.jdk/Contents/Home


I have put a couple of aliases in my `.bashrc` which allow me to toggle easily between different versions:
```bash
alias set-java8="export JAVA_HOME=<java8home>"
alias set-java10="export JAVA_HOME=<java10home>"
```

_Check your version_ by doing:
```bash
lein -version
```
I get: 
> Leiningen 2.8.1 on Java 10.0.2 Java HotSpot(TM) 64-Bit Server VM


## Java Platform Module System

JPMS a.k.a. Jigsaw was introduced with Java 9.  
[Article on Wikipedia](https://en.wikipedia.org/wiki/Java_Platform_Module_System)

George can be build as JPMS (with a `module-info.java`).  
_It will, however, not run as JPMS as Clojure itself is not compatible with JPMS._

Also, we don't want to build George into the runtime as a module, but in stead have it run in "legacy mode" against a "neutral" runtime.


## Build

Builds are done using a mix of aliases, profiles and plugins.

### The runtime

To build the runtime, do:
```bash
lein build-jre
```

This will result in an OS-specific custom runtime in the directory `<project_root>/runtime` .  
It will include only the modules "required" in `<project_root>/src/main/java/modules-info.java`

The command simply uses jlink to build the runtime, linking in the modules listed as keyword in a special construct at the top of the project file.


### The jar

To build the jar, do:
```bash
lein build-jar
```

This results in a jar-file without module-info.class.  When run - either on standard java runtime or custom runtime, it will be run in "legacy" mode.  

###  Both

To build both of the above, do:
```bash
lein build-all
```


## Run

A mix of aliases et al allow you to run what you have build.

Running something assumes you have done the appropriate builds first.

### Custom JRE

To run anything using `java` in the custom runtime, you can do:
```bash
lein xjava
```
Try doing:
```bash
lein xjava --list-modules
```

If you do:
```bash
lein java --list-modules
```
... you will se that the modules list is much longer for `java` than for `xjava`.



### Jar on JRE

To run the jar on the custom JRE, do:
```bash
lein xrun-jar
```

To run the jar on standard java, do:
```bash
lein run-jar
```


### In Leiningen

To run the project in Leingen, do:
```bash
lein run
```

This simply runs the code in Leiningen using the standard java.

_It is not possible to run Leiningen using the custom jre, as Leiningen requires a JDK JRE:_

> Java compiler not found; Be sure to use java from a JDK
  rather than a JRE by modifying PATH or setting JAVA_CMD.

  
### In a repl

When developing you will want to use standard repl development mechanims.  There are currently no special repl tools integrated in George this.  But by un-commenting one of the lines at the bottom of `george.application.launcher`, a new instance of George is launched in the repl whenever you (re-)load that module. 



## Clean

To allow for building jar and runtime in separate steps without deleting the result of each other, the default command `lein clean` only cleans the standard build target, not the runtime build target.  
To clean everything, do:
```bash
lein clean-all
``` 

