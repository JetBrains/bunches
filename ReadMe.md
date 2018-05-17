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

