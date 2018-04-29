package nk.patchsets.git

import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import java.io.File


fun main(args: Array<String>) {
    val path = "C:\\Projects\\patchsets\\patches-wt"
    val repository = configureRepository(path)
    val git = Git(repository)

    val head = repository.resolveEx(Constants.HEAD)!!
    val commit = git.log().add(head).setMaxCount(1).call().first()
    println(commit.shortMessage)

    val demo = File("C:\\Projects\\patchsets\\patches-wt\\demo.txt")
    val number = if (!demo.exists()) {
        demo.createNewFile()
        demo.writeText("1")
        1
    } else {
        val text = demo.readText()
        val lines = text.split("\n")
        val number = lines.size + 1
        demo.writeText(text + "\n" + number)
        number
    }

    git.add().addFilepattern("demo.txt").call()

    val commitCommand = git.commit().setMessage("Demo $number")
    commitCommand.callEx()
}

fun Repository.resolveEx(revstr: String): ObjectId? {
    return resolve(if (isBare) revstr else revstr.replace(Constants.HEAD, fullBranchWithWorkTree))
}

fun CommitCommand.callEx() {
    if (repository.isBare) {
        call()
    } else {
        nativeCall(repository.workTree)
    }
}

fun CommitCommand.nativeCall(dir: File) {
    val args = ArrayList<String>().apply {
        add("git")
        add("commit")
        add("-m")
        add(message)
    }

    val pb = ProcessBuilder(args)
            .inheritIO()
            .directory(dir)

    val process = pb.start()
    process.waitFor()
    val exitValue = process.exitValue()

    process.destroy()

    if (exitValue != 0) {
        System.exit(exitValue)
    }
}
