package io.github.saanliu.student.exception;

public class DuplicateStudentException extends StudentException {
    public DuplicateStudentException(String id) {
        super("Student already exists: " + id);
    }
}
