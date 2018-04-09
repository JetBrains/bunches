package nk.patchsets.git


import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LogCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import java.io.File
import java.io.IOException

class ConfigureGitException(path: String, cause: Throwable? = null) :
        Throwable("Couldn't configure git at ${File(path).absolutePath}", cause)

private val COMMON_DIR_FILE_NAME = "commondir"

private fun configureRepository(path: String): Repository {
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

    class PatchedFileRepositoryBuilder : FileRepositoryBuilder() {
        override fun setWorkTree(workTree: File?): PatchedFileRepositoryBuilder {
            super.setWorkTree(workTree)
            return this
        }

        fun setGitDirFromWorkTree(): PatchedFileRepositoryBuilder {
            setupGitDir()

            try {
                val gitCommonDirRelativePath = File(gitDir, COMMON_DIR_FILE_NAME).readText().trim()
                val gitCommonDirFile = File(gitDir, gitCommonDirRelativePath)

                gitDir = gitCommonDirFile
            } catch (io: IOException) {
                throw ConfigureGitException(path, io)
            }

            return this
        }
    }

    return PatchedFileRepositoryBuilder()
            .setWorkTree(gitFile.parentFile)
            .setGitDirFromWorkTree()
            .build()
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

fun readCommits(repositoryPath: String, untilRevString: String?, sinceRevString: String): List<CommitInfo> {
    return readCommits(repositoryPath) { git ->
        val untilQuery = untilRevString ?: repository.fullBranchWithWorkTree
        val untilCommitObjectId = git.repository.resolve(untilQuery)
        val sinceCommitObjectId = git.repository.resolve(sinceRevString)

        addRange(sinceCommitObjectId, untilCommitObjectId)
    }
}

fun readCommits(repositoryPath: String, revString: String?, numberOfCommits: Int): List<CommitInfo> {
    return readCommits(repositoryPath) { git ->
        val resolveQuery = revString ?: repository.fullBranchWithWorkTree
        val startCommitObjectId = git.repository.resolve(resolveQuery)

        add(startCommitObjectId).setMaxCount(numberOfCommits)
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

    git.commit()
            .setAuthor(commitInfo.author)
            .setMessage("$suffix: ${commitInfo.message}")
            .call()
}

enum class ChangeType { ADD, REMOVE, MODIFY }
class FileChange(val type: ChangeType, val file: File)

fun commitChanges(repositoryPath: String, changeFiles: Collection<FileChange>, title: String) {
    val repoPath = File(repositoryPath)

    val repository = configureRepository(repositoryPath)
    val git = Git(repository)

    val addCommand = git.add()
    var hasAdd: Boolean = false

    val rmCommand = git.rm().setCached(true)
    var hasRm: Boolean = false

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

    git.commit()
            .setMessage(title)
            .call()
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
        val content: String)

data class CommitInfo(
        val hash: String?,
        val parentHashes: List<String>,
        val author: PersonIdent?,
        val committer: PersonIdent?,
        val time: Int,
        val title: String?,
        val message: String?,
        val fileActions: List<FileAction>)
