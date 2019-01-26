# Launch

George has an update mechanism integrated in its main entry point.


## How it works

A properties file is embedded in the jar file.  It's values are set automatically when when the jar is built. Some keys may be overridden.  For details on the keys, do:  
```bash
lein help build embed
```

The most significant key is `ts` (timestamp); a string value usually set to a string ISO date-time (yyyy-mm-ddThh:mm:ssZ), but it can be set to any string value, as a simple string comparison is used to determine order/newest (which works perfectly for ISO date-time).

The checking and updating is recursive in nature, and goes as follows:

On launch the "original" jar is loaded (the one installed by the native installer together with the JRE and launchscript). It views itself as "this".
It attempts to locate a properties file online based on the `uri` in its embedded properties. 

If the online version is available and newer, then it will download and "install" that jar-file in a dedicated install directory.  That will now be the "installed" version. It will then load the jar in a clean classloader and a dedicated static method there will be called.

If the online version is _not_ newer (or not available), it will proceed to check the installed version.

If there is an installed version and that version is newer, then that will be loaded in a clean classloader and and the static method called.

If there is no installed version or the installed version is not newer, then it will proceed to "run" its own main code.

If the installed version is is the one being called (because it was downloaded or because it was newer), it in turn will now view itself as "this". (It doesn't know that it is "installed", not "original"), and it will proceed again from the top!

Why do a new online check, you may ask? Well, it may contain a different `uri` in its properties file than the previous version, and so might know of a newer version somewhere else. This is unlikely to happen, but it future-proofs the system - allowing us to move or file to a new online location.

So, worst case, it will again download a newer version, and around we go.

Otherwise it will move on to checking the installed version, and finding it is _not_ newer than itself, of course, as it _is_ the installed version, it will run itself.


## Example senarios

For ease of reading will will use letters A, B, C, D instead of timestamps.  B is newer than A, etc.

Scenario:  
You just downloaded the latest native installer containing version A.  
The online version is _not_ newer and there is no installed version:  
When A launches it will run itself.

Scenario:
You originally installed version A, but version B has been published:  
When A launches it will find version B online and download/install it, then launch it.
Version B will _not_ find a newer version online or installed, and so will launch itself.

Scenario:  
You originnally installed version A, which later downloaded version B.  Now version C has been published, but you don't have an internet-connection:
Version C won't be found, and you end up running version B.

Scenario:  
As above, but now you are online again:  
Version A will now see version C online and download/install it (replacing version B in the process). The installed version is launched (which now is version C).  It doesn't find a newer version online or installed, and so runs itself.

  
## How to test

From the commandline there are tools in Leiningen and arguments you can pass to George on launch that can block behavior.

Some options are:  

When building a jar you can insert a string of your choice as `ts`, allowing you to manipulate George's perception of which is "newer".

You can "install" a built jar directly by doing:
````bash
lein installed install
````
You can clean (delete all content) from the installed dir by doing:

```bash
lein installed clean
```

When you launch the jar, you can pass arguments.  For list of possible arguments, do:
```bash
lein run :help
```
There is a built-in server that can be used for simulating online testing, and more.

There are runnable "test" scripts (for *nix) in the bin-directory.

To get an overview of all built-in tools, do:
```bash
lein george
```
Then explore the commands from there using:
```bash
lein help <task> [subtask]
```

## Moving forwards

We would like to build an integrated test-suite for this part of the application.  Input and help would both be appreciated.
