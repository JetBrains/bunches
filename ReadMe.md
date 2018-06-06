[![internal JetBrains project](http://jb.gg/badges/internal-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

## Bunches Tool Set

Set of tools for storing similar git-branches in a single branch by moving files that have changes to 
*bunch-files*, files with additional special extension.

```
plugin.xml
plugin.xml.172 <-- Bunch file for Idea 2017.2
SomeAction.java
SomeAction.java.181 <-- Bunch file for Idea 2018.1
```

Such fake branches have several advantages for plugin development over classic approach when each IDEA version is stored in a separate branch:
* Files for each version are available locally. "Compare Files" action in IDE can be used to see the difference in files.
* Each commit can have own changes for different IDEA-versions. 
* Conflicting problems can be spotted and fixed before commit.
* No tedious git branch administration.

The main disadvantages are:
* Bunch files should be removed before building artifacts if build tool tends to pack them into binaries.
* Bunch files should be processed during commit phase even if there are no conflicts from classic git merge perspective (different parts of file are changed).

```
This approach works well when number of bunch-files is reasonable small or they never change. 
The best practice is to have bunch files only for solving API compatibility issues, and maintain 
same source for all supported IDEA-versions in other files.
```

This tool set is used in [Kotlin](https://github.com/JetBrains/kotlin) project.  

## Install

The latest release can be obtained from:

https://github.com/JetBrains/bunches/releases

macOS users can install it via brew:

```
brew tap jetbrains/utils
brew install bunches
```

## Operations

* **switch** - restores state of files for the particular branch. This command is used during the build, and should be used if development with non-based platform is needed.
* **cp** - cherry-picks commit to master branch with auto-creating bunch files with given suffix for all affected files.
* **cleanup** - removes all bunch files from the repository directory. This command is executed on buildserver to avoid appearing bunch files in result binaries. `--ext=<suffix>` option can be passed to clean bunch files with the specific extension.
* **check** - checks the range of commits for forgotten bunch files.
* **reduce** - locates bunch files that are equal to base files (an equality check ignores whitespaces).

## .bunch file format

```
173 // Base branch
// Switch rules
172
as31
as32_181
181
182_181
```

Each line from switch rules section describes how to switch to the branch mentioned at the beginning of the line.
For example for Android Studio 3.2, rule `as32_181_173` will be applied (base `173` branch is added implicitly).

Switch tool will do following steps when it gets `as32_181_173` as input for each base file in repository:

* If `as32` bunch-file is present, replace base file with it and continue to other file;
* If `181` bunch file is present, replace base file with it and continue to other file;
* Leave base version of the file (file from `173` branch) and continue to other file;
