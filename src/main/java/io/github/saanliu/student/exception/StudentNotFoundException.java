package io.github.saanliu.student.exception;

public class StudentNotFoundException extends StudentException {
    public StudentNotFoundException(String id) {
        super("Student not found: " + id);
    }
}
