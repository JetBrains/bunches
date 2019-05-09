package org.jetbrains.bunches.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LogCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.jetbrains.bunches.general.exitWithError
import java.io.File
import java.io.IOException

class ConfigureGitException(path: String, cause: Throwable? = null) :
    Throwable("Couldn't configure git at ${File(path).absolutePath}", cause)

private const val COMMON_DIR_FILE_NAME = "commondir"

fun configureRepository(path: String): Repository =
    GitRepositoryBuilder.create(path)

fun Repository.resolveEx(revstr: String): ObjectId? {
    return resolve(if (isBare) revstr else revstr.replace(Constants.HEAD, fullBranchWithWorkTree))
}

fun CommitCommandEx.callEx() {
    if (repository.isBare) {
        call()
    } else {
        nativeCall(repository.workTree)
    }
}

fun Git.commitEx() = CommitCommandEx(repository)

private fun CommitCommandEx.nativeCall(dir: File) {
    val args = arrayListOf(
        "git",
        "commit",
        "--quiet",
        "-m",
        message.replace("\"", "\\\"")
    )

    if (author != null) {
        args.add("--author=${author.toExternalString()}")
    }

    (when {
        committer != null -> "committer"
        isAll() -> "-a"
        isAllowEmpty() -> "--allow-empty"
        isNoVerify() -> "--no-verify"
        isAmend() -> "--amend"
        onlyPaths().isNotEmpty() -> "--only"
        else -> null
    })?.let { unsupportedOption -> throw IllegalStateException("$unsupportedOption is currently unsupported for in native commit") }

    val pb = ProcessBuilder(args)
        .inheritIO()
        .directory(dir)

    val process = pb.start()
    process.waitFor()
    val exitValue = process.exitValue()

    process.destroy()

    if (exitValue != 0) {
        exitWithError("git exit code is $exitValue")
    }
}

open class GitRepositoryBuilder : BaseRepositoryBuilder<GitRepositoryBuilder, Repository>() {
    fun setGitDirFromWorkTree(): GitRepositoryBuilder {
        setupGitDir()

        val gitCommonDirRelativePath = File(gitDir, COMMON_DIR_FILE_NAME).readText().trim()
        val gitCommonDirFile = File(gitDir, gitCommonDirRelativePath)


        indexFile = File(gitDir, "index")
        gitDir = gitCommonDirFile

        return self()
    }

    @Throws(IOException::class)
    override fun build(): Repository {
        val repo = FileRepository(this.setup())
        return if (this.isMustExist && !repo.objectDatabase.exists()) {
            throw RepositoryNotFoundException(this.gitDir)
        } else {
            repo
        }
    }

    companion object {
        fun create(path: String): Repository {
            val pathFile = File(path)
            val gitFile = if (pathFile.name == Constants.DOT_GIT) {
                pathFile
            } else {
                File(pathFile.absolutePath, Constants.DOT_GIT)
            }

            if (!gitFile.exists()) {
                throw ConfigureGitException(path)
            }

            val isWorkTree = gitFile.isFile
            if (!isWorkTree) {
                return FileRepositoryBuilder.create(gitFile)
            }

            try {
                return GitRepositoryBuilder()
                    .setWorkTree(gitFile.parentFile)
                    .setGitDirFromWorkTree()
                    .build()
            } catch (io: IOException) {
                throw ConfigureGitException(path, io)
            }
        }
    }
}

private val Repository.fullBranchWithWorkTree: String
    get() {
        if (workTree == null) {
            return fullBranch
        }

        return FileRepositoryBuilder().setWorkTree(workTree).build().use { repository ->
            repository.fullBranch
        }
    }

private fun Repository.resolveOrFail(revStr: String) =
    resolveEx(revStr) ?: throw IllegalArgumentException("revision '$revStr' was not found")

fun readCommits(repositoryPath: String, untilRevString: String?, sinceRevString: String): List<CommitInfo> {
    return readCommits(repositoryPath) { git ->
        val untilQuery = untilRevString ?: Constants.HEAD
        val untilCommitObjectId = git.repository.resolveOrFail(untilQuery)
        val sinceCommitObjectId = git.repository.resolveOrFail(sinceRevString)

        addRange(sinceCommitObjectId, untilCommitObjectId)
    }
}

fun readCommits(repositoryPath: String, logCommandSetup: LogCommand.(git: Git) -> LogCommand): List<CommitInfo> {
    val repository = configureRepository(repositoryPath)

    Git(repository).use { git ->
        return git.log()
            .logCommandSetup(git)
            .call()
            .map { commit ->
                with(commit) {
                    CommitInfo(
                        hash = ObjectId.toString(id),
                        parentHashes = parents.map { ObjectId.toString(it) },
                        author = authorIdent,
                        committer = committerIdent,
                        time = commitTime,
                        title = shortMessage,
                        message = fullMessage,
                        fileActions = collectActions(git, commit)
                    )
                }
            }
    }
}

fun reCommitPatched(repositoryPath: String, commitInfo: CommitInfo, suffix: String) {
    val repository = configureRepository(repositoryPath)
    val git = Git(repository)

    val addCommand = git.add()
    for (fileAction in commitInfo.fileActions) {
        val path = fileAction.newPath + ".$suffix"
        val newFile = File(repositoryPath, path)
        newFile.parentFile.mkdirs()
        newFile.writeText(fileAction.content)
        addCommand.addFilepattern(path)
    }
    addCommand.call()

    git.commitEx()
        .setAuthor(commitInfo.author)
        .setMessage("$suffix: ${commitInfo.message}")
        .callEx()
}

enum class ChangeType { ADD, REMOVE, MODIFY }
class FileChange(val type: ChangeType, val file: File)

fun commitChanges(repositoryPath: String, changeFiles: Collection<FileChange>, title: String) {
    val repoPath = File(repositoryPath)

    val repository = configureRepository(repositoryPath)
    val git = Git(repository)

    val addCommand = git.add()
    var hasAdd = false

    val rmCommand = git.rm().setCached(true)
    var hasRm = false

    for (changeFile in changeFiles) {
        val filePath = changeFile.file.relativeTo(repoPath).path.replace('\\', '/')
        when (changeFile.type) {
            ChangeType.ADD,
            ChangeType.MODIFY -> {
                hasAdd = true
                addCommand.addFilepattern(filePath)
            }
            ChangeType.REMOVE -> {
                hasRm = true
                rmCommand.addFilepattern(filePath)
            }
        }
    }

    if (hasAdd) {
        addCommand.call()
    }

    if (hasRm) {
        rmCommand.call()
    }

    git.commitEx()
        .setMessage(title)
        .callEx()
}

fun collectActions(git: Git, commit: RevCommit): List<FileAction> {
    val reader = git.repository.newObjectReader()

    val oldTreeIterator =
        if (commit.parentCount != 0) {
            CanonicalTreeParser().apply {
                reset(reader, commit.getParent(0).tree)
            }

        } else {
            EmptyTreeIterator()
        }

    val newTreeIterator = CanonicalTreeParser().apply {
        reset(reader, commit.tree)
    }

    val diffCommand = git.diff().apply {
        setOldTree(oldTreeIterator)
        setNewTree(newTreeIterator)
    }

    return diffCommand.call().flatMap { entry: DiffEntry ->
        when (entry.changeType) {
            DiffEntry.ChangeType.RENAME -> {
                listOf(
                    FileAction(entry.changeType, entry.oldPath, ""),
                    FileAction(entry.changeType, entry.newPath, entry.readNewContent(git))
                )
            }
            DiffEntry.ChangeType.DELETE -> {
                listOf(FileAction(entry.changeType, entry.oldPath, ""))
            }

            DiffEntry.ChangeType.ADD,
            DiffEntry.ChangeType.MODIFY,
            DiffEntry.ChangeType.COPY -> {
                listOf(FileAction(entry.changeType, entry.newPath, entry.readNewContent(git)))
            }
            else -> listOf()
        }
    }
}

fun DiffEntry.readNewContent(git: Git): String {
    val loader = git.repository.open(newId.toObjectId())
    return String(loader.bytes)
}

data class FileAction(
    val changeType: DiffEntry.ChangeType?,
    val newPath: String?,
    val content: String
)

data class CommitInfo(
    val hash: String?,
    val parentHashes: List<String>,
    val author: PersonIdent?,
    val committer: PersonIdent?,
    val time: Int,
    val title: String?,
    val message: String?,
    val fileActions: List<FileAction>
)
