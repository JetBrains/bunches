package nk.patchsets.git

import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import java.io.PrintStream

/**
 * Mimic all interface of CommitCommand but provide more accessors.
 */
class CommitCommandEx(repository: Repository) : CommitCommand(repository) {
    private var noVerify: Boolean = false
    private var all: Boolean = false
    private var allowEmpty: Boolean = false
    private val only: MutableSet<String> = HashSet<String>()
    private var amend: Boolean = false

    override fun setAll(all: Boolean): CommitCommandEx {
        this.all = all
        return super.setAll(all).self
    }

    fun isAll() = all

    override fun setNoVerify(noVerify: Boolean): CommitCommandEx {
        this.noVerify = noVerify
        return super.setNoVerify(noVerify).self
    }

    fun isNoVerify() = noVerify

    override fun setAllowEmpty(allowEmpty: Boolean): CommitCommandEx {
        this.allowEmpty = allowEmpty
        return super.setAllowEmpty(allowEmpty).self
    }

    fun isAllowEmpty() = allowEmpty

    override fun setOnly(onlyPath: String): CommitCommandEx {
        only.add(onlyPath)
        return super.setOnly(onlyPath).self
    }

    fun onlyPaths(): Set<String> = only

    override fun setAmend(amend: Boolean): CommitCommandEx {
        this.amend = amend
        return super.setAmend(amend).self
    }

    fun isAmend() = amend

    override fun setMessage(message: String?) = super.setMessage(message).self
    override fun setCommitter(committer: PersonIdent?) = super.setCommitter(committer).self
    override fun setCommitter(name: String?, email: String?) = super.setCommitter(name, email).self
    override fun setAuthor(author: PersonIdent?) = super.setAuthor(author).self
    override fun setAuthor(name: String?, email: String?) = super.setAuthor(name, email).self
    override fun setReflogComment(reflogComment: String?) = super.setReflogComment(reflogComment).self

    override fun setInsertChangeId(insertChangeId: Boolean) =
        super.setInsertChangeId(insertChangeId).self

    override fun setHookOutputStream(hookStdOut: PrintStream?) =
        super.setHookOutputStream(hookStdOut).self

    override fun setHookOutputStream(hookName: String?, hookStdOut: PrintStream?) =
        super.setHookOutputStream(hookName, hookStdOut).self

    private val CommitCommand.self: CommitCommandEx get() = this@CommitCommandEx
}