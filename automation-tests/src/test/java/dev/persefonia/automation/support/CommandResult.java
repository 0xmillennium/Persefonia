package dev.persefonia.automation.support;

public record CommandResult(int status, String stdout, String stderr) {
    public void requireSuccess() {
        if (status != 0) {
            throw new AssertionError("Command exited " + status + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr);
        }
    }
}
