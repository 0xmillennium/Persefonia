package dev.persefonia.app.platformoperations.operations;

public final class OperationsReadException extends RuntimeException {
    public OperationsReadException() {
        super("Operational state could not be read safely.");
    }
}
