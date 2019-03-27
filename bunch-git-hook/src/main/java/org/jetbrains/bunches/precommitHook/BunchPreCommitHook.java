package org.jetbrains.bunches.precommitHook;

import static java.lang.System.exit;

public class BunchPreCommitHook {
    public static void main(String[] args) {
        if (args.length != 0) {
            exit(1);
        }
        System.out.println("Hello world");
        exit(0);
    }
}
