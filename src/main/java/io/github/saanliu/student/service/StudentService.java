package io.github.saanliu.student.service;

import io.github.saanliu.student.exception.DuplicateStudentException;
import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentNotFoundException;
import io.github.saanliu.student.model.ScoreStatistics;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.StudentRepository;

import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StudentService {
    private static final double PASSING_SCORE = 60.0;

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

        Student updated = new Student(student.id(), student.name(), student.className(), score);
        repository.save(updated);
        return updated;
    }

    public List<Student> getAllStudents() {
        return repository.findAll().stream()
                .sorted(Comparator.comparingDouble(Student::score).reversed()
                        .thenComparing(Student::id))
                .toList();
    }

    public Optional<ScoreStatistics> getStatistics() {
        List<Student> students = repository.findAll();
        // 一次遍历得到人数、平均分、最高分和最低分。
        DoubleSummaryStatistics summary = students.stream()
                .mapToDouble(Student::score)
                .summaryStatistics();
        if (summary.getCount() == 0) {
            return Optional.empty();
        }

        long passingCount = students.stream()
                .filter(student -> student.score() >= PASSING_SCORE)
                .count();
        // 先转换为浮点数，避免整数除法丢失小数部分。
        double passRate = passingCount * 100.0 / summary.getCount();

        Map<String, Double> classAverages = students.stream()
                .collect(Collectors.groupingBy(Student::className, TreeMap::new,
                        Collectors.averagingDouble(Student::score)));

        return Optional.of(new ScoreStatistics(summary.getCount(), summary.getAverage(),
                summary.getMax(), summary.getMin(), passRate, classAverages));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentException(fieldName + " must not be blank.");
        }
        return value.strip();
    }

    private void validateScore(double score) {
        if (!Double.isFinite(score) || score < 0 || score > 100) {
            throw new InvalidStudentException("Score must be a finite number between 0 and 100.");
        }
    }
}
