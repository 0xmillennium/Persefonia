package dev.persefonia.app.transaction;

public interface PostCommitTaskExecutor {
    void afterCommit(Runnable task);
}
