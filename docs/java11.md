# Java 11


George now required Java 11.  
The main reasons for moving from Java 8 to 11:
1. Being able to build a deployable custom runtime.
2. Accessing new functionality in Java 11.

Also, we are now switching to OpenJDK due mainly to changed licencing.

You will need both OpenJDK and OpenJFX to run and work with the project.  
Currently the latest version is 11.0.2


{{TOC}}

[TOC]


## Install OpenJDK

You can download the Java 11 JDK from [jdk.java.net](https://jdk.java.net/11)  


### Windows

Download and unzip the JDK and put it in in a folder such that your path will look like:  
`C:\Program Files\Java\jdk-11.0.2`

Then:  

1. locate the "System Properties" control panel,
2. click "Environment Variables", 
3. click "New..." under "User variable for ...", 
4. and insert "JAVA_HOME" as name and the path from above as value, and "OK".  
5. Then mark the line for "Path" and click "Edit...",
6. and append `%JAVA_HOME%\bin` (either after a `;` or on a new row with "New") and "OK". 
7. And "OK" and "OK" and you're done.

Now test:  

1. Open a new Command Prompt (search for "cmd"), 
2. and do `echo %JAVA_HOME%`, and you should see your path.  
3. Then do `java -version`, and you should get some java version info.


### MacOS

Download and unpack the JDK and put it in in a folder such that your path will look like:  
`/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home`

You then need to set JAVA_HOME:

1. Open your `~./bashrc` or `~/.bash_profile` file for editing. 
2. Add the line:  <br>`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home`
3. Save.

Now test:
1. Open a new Terminal,
2. and do `echo $JAVA_HOME`, and you should see your path.
3. Then do `java -version`, and you should get some java version info.


## Multiple Java versions

You don't need to use Java 11 as your default Java, but can have it installed and available in parallel with other versions.  See the "JAVA_HOME" section bellow.

On my Mac, I have put a couple of aliases in my `.bashrc` which allow me to toggle easily between different versions:
```bash
alias set-java8="export JAVA_HOME=<java8home>"
alias set-java11="export JAVA_HOME=<java11home>"
```

_Check your version_ by doing:
```bash
lein -version
```
I get: 
> Leiningen 2.8.1 on Java 11.0.2 OpenJDK 64-Bit Server VM


## Install OpenFX

You will need both the _JavaFX SDK_ and the _JavaFX jmods_ for your platform (Windows, MacOS, or Linux).


### Scripted

For convenience there is a script which will download and set up the SDK and jmods correctly for you on your platform.
In a terminal, do one of:
```bash
./bin/setup-macos.sh
```

```cmd
bin\setup-windows.bat
```

### Manually

If you prefer to do it manually, do the following:

Download them both from [gluonhq.com](https://gluonhq.com/products/javafx/), and unpack them.

Then create directories in the project directory and place the SDK and jmods directories in them to match the following:

    george-application                # the project dir
        javafx-libs                   # create this dir
            <your platform>           # create this dir: Windows / MacOS / Linux
                javafx-sdk-11.0.2     # this directory you downloaded
                javafx-jmods-11.0.2   # this directory you downloaded

The structure has to be exact so it matches the values under `:modules :libs` and `:modules :mods` in file `project.clj`.

If everything is set up correctly, then now you are ready to start working with the code.

See <a href="tools.md">docs/tools.md</a> to learn how.


***

## Java Platform Module System

The JMS a.k.a. Jigsaw was introduced with Java 9.  
[Article on Wikipedia](https://en.wikipedia.org/wiki/Java_Platform_Module_System)

However, we currently built and run George in "legacy" mode.  There is currently no good reason to strive for "jms" mode.
