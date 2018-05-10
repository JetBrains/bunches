## 0.9.0

- Experimental git worktree feature support
- Set correct index file when working tree repository is used
- Throw exceptions on unresolved revisions *(thanks to Nicolay Mitropolsky)*
- `cp` command doc updates *(thanks to Nicolay Mitropolsky)*
- Compare only neighbour files in `reduce` command
- Add options for committing files found in `reduce` command
- Create empty files for files that were absent in base branch during `switch`
- Introduce `stats` with information about number of bunch files used
- Show deleted files in `check` command
- Use `since-ref` in `check` instead of always using `HEAD`  
- Add third-party libraries licenses
- Exit with non-zero exit code if problems found
- Filter out `out` directory from processing during `switch` 