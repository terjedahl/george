# Launch


Just some quick notes. Things may change.


## How it works

Which version to run is decided by the timestamp 'ts' in the embedded or accompagnying file 'app.properties'.
The timestamp is compared lexically (aka alphabetiacally), which works perfectly for ISO-formats.

That also means that you can override the values easily for testing.

The mechanism is as follows:
1. The application starts and refers to itself as "this".  It doesn't know where of how it was started. But it refers to it's own embedded app-file.  

2. It then check whether the "installed" version is newer, in which case it will load that version via the custom jar-loader.  (Of course it may itself be the installed version, in which case the installed version won't be newer, and so it will move on to the next step.)

3. It checks whether the "online" version is newer, in which case it will download it and load it as the installed version.  When it the new version is launched it will also check against the installed and online version, but then neither will be newer, and so it will go on simply run itself.

This potentially recursive behavior is by design, allowing for a form or download chaining (where the uri for online check may change between version.)

If there is no installed version, then this is by default newer.  
If there is no online version or no internet access, then this is by default newer.


## How to test

We need to build 3 versions.  We can override the timestamp to suit our needs.

First we want the newest version online.  Do:
```bash
lein build-jar nil nil 2018  # or simply lein build-jar
lein serve-jar               # see section "Serving"
```

Then we want a second older version installed. Do:
```bash
lein build-jar nil nil 2017  
lein install-jar
```

Finally, a third local very old version.  Do:
```bash
lein build-jar nil nil 2016
```

First check the local version only. Do:
```bash
lein run-jar --no-check
```
... and check the "About" to see the timestamp.

Now, without a server running, do:
```bash
lein run-jar
```
 ..and check the "About" again.
 
Now, start a local server and try again:
```bash
lein server-start             # not implemented yet
lein run-jar
```
 ... and check.

And again, but without the installed version. Delete the 'installed' directory manually. Find it with:
```bash
lein install-dir  # prints the install directory.
```
Then:
```bash
lein run-jar
```
.. and check.

Then, delete it again, and again test, but first to:

```bash
lein server-stop
```

Now you have tested all possible permutations.  Well done.

TODO: Implement an automated script which goes through all the above steps.


## Serving

Currently there is only one hard-coded deploy-command which uploads to a specific url on AWS.  But to facilitate easy testing, a small web-server is built in to the project.

The following commands apply:
```bash
lein serve-jar     # Copies the current built jar to a dedicated folder
lein server-start  # Serves from that folder only. The embedded url is overridden.
lein server-status # Prints server status information.
lein server-stop   # 
```

As the project server runs on [http://localhost:9999](http://localhost:9999), you will need to add that when you build your jar:
```bash
lein build-jar nil http://localhost:9999 <nil or-some-timestamp-str>
lein serve-jar                 
```

_Tip:_  
(On *nix) you can send the server to the background by starting it with a `&`:
```bash
lein serve-jar  &
```
You will still be able to stop it with `lein stop-server`.
