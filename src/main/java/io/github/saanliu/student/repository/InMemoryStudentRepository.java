package io.github.saanliu.student.repository;

import io.github.saanliu.student.model.Student;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryStudentRepository implements StudentRepository {
    private final Map<String, Student> students = new LinkedHashMap<>();

    @Override
    public void save(Student student) {
        students.put(student.id(), student);
    }

    @Override
    public Optional<Student> findById(String id) {
        return Optional.ofNullable(students.get(id));
    }

    @Override
    public void deleteById(String id) {
        students.remove(id);
    }

    @Override
    public List<Student> findAll() {
        return List.copyOf(students.values());
    }
}
