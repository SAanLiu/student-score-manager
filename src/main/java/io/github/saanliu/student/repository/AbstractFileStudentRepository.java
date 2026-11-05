package io.github.saanliu.student.repository;

import io.github.saanliu.student.model.Student;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractFileStudentRepository implements StudentRepository {
    protected final Path file;
    private List<Student> students = List.of();

    protected AbstractFileStudentRepository(Path file) {
        this.file = Objects.requireNonNull(file, "Data file must not be null.").toAbsolutePath().normalize();
    }

    protected final void loadStudents() {
        students = List.copyOf(readStudents());
    }

    @Override
    public final void save(Student student) {
        List<Student> updated = new ArrayList<>(students);
        updated.removeIf(existing -> existing.id().equals(student.id()));
        updated.add(student);
        saveChanges(updated);
    }

    @Override
    public final Optional<Student> findById(String id) {
        return students.stream().filter(student -> student.id().equals(id)).findFirst();
    }

    @Override
    public final void deleteById(String id) {
        List<Student> updated = students.stream()
                .filter(student -> !student.id().equals(id))
                .toList();
        saveChanges(updated);
    }

    @Override
    public final List<Student> findAll() {
        return List.copyOf(students);
    }

    private void saveChanges(List<Student> updated) {
        List<Student> snapshot = List.copyOf(updated);
        // 子类写入失败会抛出异常，此时不更新当前内存快照。
        writeStudents(snapshot);
        students = snapshot;
    }

    protected abstract List<Student> readStudents();

    protected abstract void writeStudents(List<Student> students);
}
