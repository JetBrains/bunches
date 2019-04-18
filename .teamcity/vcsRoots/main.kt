package vcsRoots

import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object BunchToolVcsRoot : GitVcsRoot({
    name = "Bunch Tool"
    url = "file:///C:/Projects/bunches"
})