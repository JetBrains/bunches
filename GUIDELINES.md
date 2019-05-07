# Bunch-tool common guidelines

Those guidelines cover most common scenarios and generally useful for a usual developer, meaning that they won't help you if
you have a tough luck of being deeply involved with bunches and support of different IDE versions (in which case, you 
probably don't need guidance anyways :) )

## What should I do to not break bunch-versions accidentally?

The recommended way is to install pre-commit hook (if you're using CLI for working with Git), or just use IntelliJ IDEA
support for Git, which has built-in support of bunches.

Another, less convenient way, is to just watch for status of your remote-runs. If your changes introduce some compatibility 
issues, most likely you'll see failing tests in corresponding configurations (like, `IDEA Plugin tests (183)` for a IDEA 183 
bunch). 

## I need to make changes in bunch-files, what should I do?

For the sake of example, suppose that:

* you are on a branch `my-feature`, 
* default bunch is `191`
* you have made changes to a file `foo.kt`, 
* this file has a bunch-version `foo.kt.183`, 
* you know that this bunch versions requires changes too

**Option #1. Plain-text editing**
You can just edit bunch-versions (`foo.kt.183` in our example) manually, in a plain-text mode, and commit changes to them. 
This is often enough for simple changes and/or small bunch-files. 


> Tip: you can use `Compare With...` (Ctrl+D/Cmd+D by default) in IntelliJ IDEA to see visual diff of files


**Option #2. Full-switch + cherry-pick changes automatically**
For more complicated changes, you're usually would like to have a proper highlighting and other IDEA features in bunch 
files. In this case, you can try following workflow:

* create and checkout new branch, e.g. `git checkout -b my-feature-183`
* Switch to the needed bunch: `bunch switch . 183
    foo.kt` will be renamed to `foo.kt.191
    foo.kt.183` will be renamed to `foo.kt`, allowing to work with it in IDE
* Make changes to the `foo.kt` and commit them
* Now, `git log` should look like this:

```
Adjust foo.kt in 183-bunch (HEAD -> my-feature-183)
~~~ switch 183 ~~~
Change foo.kt (my-feature)
...
```

Now, we have to migrate changes from the 183-bunch into the main one. `bunch`-tool provides an automated way to do this via `bunch cp`. 

* switch back to `my-feature`
* use `bunch cp`: `bunch cp . my-feature-183 my-feature-183~`
    * See the syntax reference in `bunch cp -help`
* `bunch`-tool will cherry-pick your commit, applying its changes to the correct bunch-version of modified files.  
   In our example, `bunch`-tool will detect that you're cherry-picking changes from the `183`-version to the `191` one and 
   will apply them to `foo.kt.183` instead of `foo.kt`
* (optionally, but highly recommended) squash cherry-picked commit with changes to bunch-file into original commit

> IMPORTANT NOTE!

It is **strongly discouraged** to introduce **new** bunch-files in this way. If you've made bunch-changes to files which 
used not to have bunch-versions, please, consider porting changes manually to make sure that all newly introduced bunch-
versions are indeed necessary.

## How do I build some bunch-version locally?

Just use `bunch switch` command, e.g., `bunch switch . 183` . Bunch tool will automatically adjust working tree to match 183 
bunch, changing content of files to `.183`-bunch if any and backing up current content to `.191`-bunch files.

All changes will be collected into a temporary commit with name like `~~~ switch 183 ~~~`. 

After that, you can build project as always and it will be compiled against selected bunch-version.

When you're finished, you can just drop switch-commit (`git reset —hard HEAD~ `assuming that switch-commit is the last 
commit), and the state of the repository will be restored to the default bunch.
