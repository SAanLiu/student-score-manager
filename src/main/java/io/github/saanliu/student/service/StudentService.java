package io.github.saanliu.student.service;

import io.github.saanliu.student.exception.DuplicateStudentException;
import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentNotFoundException;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.StudentRepository;

import java.util.List;
import java.util.Objects;

public class StudentService {
    private final StudentRepository repository;

    // 依赖接口，具体使用内存还是文件由程序启动时决定。
    public StudentService(StudentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
    }

    public Student addStudent(String id, String name, String className, double score) {
        String normalizedId = requireText(id, "ID");
        String normalizedName = requireText(name, "Name");
        String normalizedClassName = requireText(className, "Class name");
        validateScore(score);

        if (repository.findById(normalizedId).isPresent()) {
            throw new DuplicateStudentException(normalizedId);
        }

        Student student = new Student(normalizedId, normalizedName, normalizedClassName, score);
        repository.save(student);
        return student;
    }

    public Student getStudent(String id) {
        String normalizedId = requireText(id, "ID");
        return repository.findById(normalizedId)
                .orElseThrow(() -> new StudentNotFoundException(normalizedId));
    }

    public void deleteStudent(String id) {
        Student student = getStudent(id);
        repository.deleteById(student.id());
    }

    public Student updateScore(String id, double score) {
        Student student = getStudent(id);
        validateScore(score);

        // record 不提供 setter，修改成绩时创建新对象并替换旧对象。
        Student updated = new Student(student.id(), student.name(), student.className(), score);
        repository.save(updated);
        return updated;
    }

    public List<Student> getAllStudents() {
        return repository.findAll();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentException(fieldName + " must not be blank.");
        }
        return value.strip();
    }

    private void validateScore(double score) {
        // NaN 与普通数字的大小比较均为 false，因此需要单独检查有限值。
        if (!Double.isFinite(score) || score < 0 || score > 100) {
            throw new InvalidStudentException("Score must be a finite number between 0 and 100.");
        }
    }
}
