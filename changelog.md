## 0.9.1 (Apr 23, 2018)

- Ignore all `out` directories in `reduce`, `stats`, and `switch` commands
- Support `ls` mode in `stats` command to see how bunch files are spread among subdirectories
- Allow to execute `stats` command for repository subdirectories 
- Some files were not shown as redundant in `reduce` command

## 0.9.0 (Apr 10, 2018)

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