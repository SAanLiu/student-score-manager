package io.github.saanliu.student.exception;

public class StudentStorageException extends StudentException {
    public StudentStorageException(String message) {
        super(message);
    }

    public StudentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
