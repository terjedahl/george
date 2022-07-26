# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).


## [2019.1.5] - 2019-10-04

### Fixed
- When starting from an "installed" jar, the classloader was not set in Swing's rendering thread, causing errors when loading service providers et al.  


## [2019.1.4] - 2019-10-01

### Fixed
- An issue where "default" microphone would be included in list of possibles when name was translated by OS.
- An issue where fetching a line from a bad or missing microphone whould hang the process.


## [2019.1.3] - 2019-06-13

The main feature in this version is license handling in George Projects.

### Added
- George Projects: Complete license and unlock mechanism 
- Basic george-server setup.

### Fixed
- George Projects: '<' and '>' were being replaced with '&lt;' and '&gt;'.


## [2019.1.2] - 2019-05-09

### Improved
- George Projects: Better diffing with "unified view" of code in project steps.
- George Projects: Checkbox for code-mode in settings, and copy button (in code-mode).


## [2019.1.1] - 2019-04-29

The main feature in this deployment is the Linux/Ubuntu installer.

### Added
- Tooling for building a Linux distribution, including installer script, .desktop file support.
- A Java agent for intercepting and altering a specific JavaFX class to support setting application name.
- A workaround for known Linux KDE rendering bug in JavaFX
- JavaFX setup-scripts for three platforms.

### Improved
- George Projects: Cleaned up layout/design somewhat.
- George Projects: Added advanced "code-mode" feature - for testing and presenting only code part of each step.
- Documentation for developers/contributors.


## [2019.1] - 2019-03-23

The main highlight for this deployment is "George Projects"; A concept and a "player" for step-by-step tutorials.

### Added
- Applet/player for George Projects.

### Fixed
- TG: Text alignment of `write` would vary based on visual bounds. Also, shifted alignment from TOP to BASELINE.
- TG: `set-position` didn't handle x or y as nil (as per documentation).
- TG help: Cosmetic fixes and link-fix.

### Improved
- Improve application load-time by perhaps 30-40%.
- Reworked Inputs: Tabs replaced with vertical list à la "Open files" list.
- Version showed in place of "About" label.  Also, adjusted some styling.
- TG: Optimized speed of some TG commands.  
- TG: Enhanced "Help" documentation (added som missing commands).
- TG: Only warn once per run about nil speed in fxthread.
- TG: Crisper 1px horizontal and vertical lines.
- TG: Function 'reset-screen-size', also called by 'reset'. 
- TG: Augmented axis with 10px grid, x/y, and 'show-axis' and 'hide-axis' commands. 


## [2019.0.1] - 2019-02-02

### Fixed
- (regression) DnD in filetree causing exception.
- New/Rename file/folder uniqueness logic incorrect.
- TG: clone-turtle threw Exception if shape contained line.
- TG: reset didn't reset shape or props.
- Caret blink continuously caused scroll to caret.
- Moving or renaming a folder orphaned contained open files.
- New file dialog appeared behind turtle screen.
- (regression) Input history didn't load.
- Input history index was decremented if miss on local history.
- File dropdown is now only enabled for selected file. 

### Improved
- When a file is opened or selected the editor now automatically gets focus.
- About dialog now supports click-to-copy version info. Also, fixed font issues.
- Reveal in Explorer/Finder now marks actual file, not just parent folder.
- No disclosure-triangle if folder is empty.
- Long filenames compress rather than force filetree to scroll horizontally.
- The selected file's path doesn't wrap in the middle of the filename.


## [2019.0] - 2019-01-29

_A major technology upgrade._

### Highlights
- Native, dual install (per-user / per-machine) installers for Windows (MSI) and MacOS (PKG).
- Installs complete application; allowing for offline installations from e.g. USB-drive.
- Integrated self-updating launch.
- Java 11 and Clojure 10.
- Custom build runtime in native install.
- Multiple isolated application environments: George-DEV, George-TEST, George (PROD), allowing for end-to-end testing without effecting production.
- Full suite of build-tools as custom Leiningen tasks.


## [2018.9] - 2018-11-14

### Fixed
- An issue with fonts not loading.
- The Quit now appears on top of the turtle screen.
- In fullscreen on MacOS turtle screen should now stay in same Space as application.

### Added
- Carret now blinks.
- Resizing of text using CTRL-+/-.

### Improved
- Caret and selection colors are now blue instead of red.
- Altered design for code blocks: No fill color gives clearer text and structure.


## [2018.8] - 2018-11-07

### Added
- A file tree similar to other IDEs; with DnD and other common features.
- A list of open files which replace "tabs".
- Obligatory file creation; no more forgetting to save or lost data.
- Preservation of state; files are re-opened as they were.

### Changed
- Default project path: \<home>/Documents/George -> \<home>/George.

### Removed
- Tabbed editors; replaced by "opened" list.
- Unnamed (unsaved) files; a file must be created.


## [2018.7] - 2018-10-14

### Added
- Added support for ARM Språklab

### Fixed
- Altered JavaFX initialization mechanism
- JavaFX is not initialized during compile
- Fonts are not loaded during compile

### Improved
- Upgraded some dependencies
- Replace Thread with future everywhere
- Enhanced george.javafx
- Removed some unused files and altered project structure

### Removed
- Dynamic discovery of applets


## [2018.6.2] - 2018-09-07

### Fixed
- Compiling no longer initializes JavaFX runtime.
- JavaFX runtime now requires explisit intialization in code. As does pre-loading fonts.


## [2018.6.1] - 2018-08-28

### Improved
- Use `future` everywhere in stead of `Thread(...).start()` for improved performance.
- Improvements to `george.javafx/alert`
- Improvements to `george.javafx/stage`


## [2018.6] - 2018-07-10

### Added
- Undo/redo in text-editor

### Turtle API
- Fixes to arc/arc-left/arc-right:
    - bug-fix (negative args)
    - improved accuracy (end position and heading)
    - no speed increase
- Safer fx-thread handling in move commands (vis a vis deadlock).
- New "group" commands:
    - shapes
    - set-shape/get-shape
    - set-center
- New mouse-click commands:
    - set/get/do-onclick (for turtle)
    - set/get/do-screen-onclick
- New samples: samples/rail-maze

## [2018.5] - 2018-02-18

### Added
- A basic DnD filetree, though not activated.

### Fixed
- Metadata was being stripped before evaluation.

### Turtle API
- Reworked and extended "screen" implementation.
- New commands: set/get-screen-size, set/is-screen-visible, with-screen, get-screen, new-screen


## [2018.4.1] - 2018-02-03

### Turtle API
- Fixed a deadlock-issue between move-to and ticker.
- Renamed assoc-/dissoc-onkey to set-/unset-onkey.
- Added namespaces 'george.turtle.tom' and 'george.turtle.adhoc.jf4k'


## [2018.4] - 2018-01-31

### Turtle API
- Added support for animation, and for keyboard-input.
- Implemented a long list of new commands:
is-overlap, get-overlappers, set-/get-/reset-/start-/stop-ticker/is-ticker-started, assoc-/dissoc-/get-/get-all/reset-onkey, to-front
- And a new demo: samples/asteroids

## [2018.3] - 2018-01-25

### Added
- Better handling of prep-ing of default turtle-namespace, using macros.
- 2 special macros: `g/turtle-ns` and `g/create-turtle-ns` which behave pretty much like their counterparts in clojure.core. 

### Changed
- Reorganized some namespaces.

### Turtle API
- `screen` is now thread-safe.
- Improvements to `filled` and `filled-with-turtle`
- Implemented "fencing" of screen - with :wrap/:stop/:none/functions
- Implemented 'move', 'move-to', 'turn', 'turn-to', 'distance-to', 'heading-to'
- Implemented 'arc-left', 'arc-right'
- Added a color palette to the help-window


## [2018.2] - 2018-01-16

### Added
- 'Load' command in editor, similar to 'Run' but less chatty.

### Changed
- Output now has left margin which shows the chars and colors that used to be printed, making copying from output easier, and the output tidier. 
- Rewrote a large part of the turtle API, and extended it considerably.

### Turtle API
- Pen shape control: 'set-round', 'is-round'
- Working with multiple turtles: 'new-turtle', 'clone-turtle', 'with-turtle'
- Filled figures: 'filled', 'filled-with-turtle', 'set-fill', 'get-fill'
- Writing on-screen: 'write', 'set-font', 'get-font'
- Running multiple turtles concurrently (in threads): Use 'future'


## [2018.1] - 2018-01-11

### Fixed
- Regression: CTRL-C now is "copy" in editor again, not "close tab"
- \*err\* messages from nrepl now also get printed.
- A nagging JavaFX Toolkit load/repl issue.

### Added
- Extensive master/detail "Turtle API" window, pulling content from docs and other texts in turtle API.
- Markdown parsing and HTML rendering of/for Turtle API. 
- New turtle commands: 'set-width'/'get-width', 'set-visible', 'set-pen-down'.
- Enhanced color handling in Turtle API.
- Library "defprecated" now prints warning when deprecated "turtle commands" are used.

### Changed
- Altered name of certain turtle "getter" commands.  
- Moved previous minimal embedded command list into a separate "Turtle API" tool window with link.
- Select color in editor now becomes gray when editor loose focus.
- Clojure 1.8 -> 1.9
- Sensible defaults: 1 editor and 1 input open, and input's "clear" not checked.
- Moved namespace 'george.application.turtle.turtle' to 'george.turtle'

### Removed
- Unused modules from code - including Paredit and cider-nrepl.


## [2018.0] - 2018-01-04

This is a major upgrade, with many changes.  
A few highlight:

- Single window application
- New custom text-editor with Parinfer and "blocks"
- Editor in tabs with robust file handling
- Enhanced REPL usage, error handling, nREPL server control
- Improved L&F


## [0.8.2] - 2017-10-11

### Changed
- Removed keyboard shortcuts from "history" buttons, as they were often accidentally triggered while navigating in code-editor.

### Fixed
- Using undo/redo is now more stable and safe. It should no longer cause rendering artifacts or multiple (or no) cursors.


## [0.8.1] - 2017-05-17

### Changed
- Adjusted coloring of code.

### Fixed
- Turtle screen does not persistently take focus during code execution.
- Paredit now works better - parens stay matched(!), and "slurp", "barf", "raise" work.  Also, better handling of marking and cursor location.
- Starting a Run/Eval via keyboard shortcut for is now also disabled during an ongoing run.

### Added
- Ability to copy or save Turtle screen snapshot from contextual menu.
- Resizing code (text) via CTRL/CMD-+/- - from 6 to 72 px.
- 'set-speed' in Turtle API - 10 is default 15 is as fast as it will animate, 'nil' skips all animation.
- A drop-down menu (in Input) disables/enables Paredit.


## [0.8.0] - 2017-05-03

### Changed
- George now uses nREPL for all evaluation - instead of custom REPL.

### Added
- True REPL/Eval interrupt from Input-window.
- Error-dialog informing user of error if Output not open.
- Stacktrace in Output and Error-dialog - uses clj-stacktrace.
- Attempts to parse location of error - displayed in Output and Error-dialog.


## [0.7.4] - 2017-04-19

### Changed
- Input window gets focus after execution/Eval.

### Fixed
- Long-running Eval no longer prevents George from exiting


## [0.7.3] - 2017-03-27

### Changed
- Input stages now "stagger" their layout nicely.
- Other adjustments to layout - to accomodate very small computer screens.
- Eval-button is disabled during execution - to prevent users from running code multiple times in (conflicting) threads.

### Added
- About label/button on launcher.
- A new Turtle command 'rep' - a simpler version of Clojures 'dotimes'.
- A small window listing basic available Turtle commands.


## [0.7.2] - 2017-03-18 [UNDEPLOYED]

### Removed
- The "IDE" application on the launcher. It caused confusion and errors.


## [0.7.1] - 2017-02-21

### Changed
- Most windows change from "tool pallets" to standard windows.

### Fixed
- A divide-by-zero exception for certain Turtle rotations. Caused an ugly printout.


## [0.7.0] - 2017-02-27

Base version open for contributions.


## [0.6.3] - 2016-11-23

Ready for course "Clojure 101"


## [0.6.2] - 2016-11-07


## [0.6.1] - 2016-10-05

First version used in a school.


<!--
[Unreleased]: https://github.com/your-name/{{name}}/compare/0.1.1...HEAD
[0.1.1]: https://github.com/your-name/{{name}}/compare/0.1.0...0.1.1
-->
