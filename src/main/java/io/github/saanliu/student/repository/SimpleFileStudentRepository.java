package io.github.saanliu.student.repository;

import io.github.saanliu.student.exception.StudentStorageException;
import io.github.saanliu.student.model.Student;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SimpleFileStudentRepository implements StudentRepository {
    private final Path file;
    private List<Student> students;

    public SimpleFileStudentRepository(Path file) {
        this.file = Objects.requireNonNull(file, "Data file must not be null.").toAbsolutePath().normalize();
        this.students = load();
    }

    @Override
    public void save(Student student) {
        List<Student> updated = new ArrayList<>(students);
        updated.removeIf(existing -> existing.id().equals(student.id()));
        updated.add(student);
        saveChanges(updated);
    }

    @Override
    public Optional<Student> findById(String id) {
        return students.stream().filter(student -> student.id().equals(id)).findFirst();
    }

    @Override
    public void deleteById(String id) {
        List<Student> updated = students.stream()
                .filter(student -> !student.id().equals(id))
                .toList();
        saveChanges(updated);
    }

    @Override
    public List<Student> findAll() {
        return List.copyOf(students);
    }

    private List<Student> load() {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Student> loaded = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                // -1 保留行尾空字段，避免漏掉空成绩等格式问题。
                String[] fields = lines.get(index).split(",", -1);
                int lineNumber = index + 1;
                if (fields.length != 4) {
                    throw new StudentStorageException("Invalid data at line " + lineNumber + " in " + file
                            + ": expected 4 fields (id,name,className,score).");
                }

                double score;
                try {
                    score = Double.parseDouble(fields[3].strip());
                } catch (NumberFormatException exception) {
                    throw new StudentStorageException("Invalid score at line " + lineNumber + " in " + file
                            + ": score must be a number.", exception);
                }
                loaded.add(new Student(fields[0], fields[1], fields[2], score));
            }
            return List.copyOf(loaded);
        } catch (NoSuchFileException exception) {
            return List.of();
        } catch (IOException exception) {
            throw new StudentStorageException("Could not read student data from " + file + ".", exception);
        }
    }

    private void saveChanges(List<Student> updated) {
        // 先检查所有字段能否往返读写，再执行任何文件操作。
        List<String> lines = updated.stream()
                .map(this::toLine)
                .toList();
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            students = List.copyOf(updated);
        } catch (IOException exception) {
            throw new StudentStorageException("Could not save student data to " + file
                    + ". No in-memory changes were applied.", exception);
        }
    }

    private String toLine(Student student) {
        validateStorageField(student.id(), "ID");
        validateStorageField(student.name(), "Name");
        validateStorageField(student.className(), "Class name");
        return student.id() + "," + student.name() + "," + student.className() + "," + student.score();
    }

    private void validateStorageField(String value, String fieldName) {
        // 这是简化文件格式的限制，不是所有 repository 都应遵守的业务规则。
        if (value.contains(",") || value.contains("\r") || value.contains("\n")) {
            throw new StudentStorageException(fieldName
                    + " cannot contain commas or line breaks with simple file storage.");
        }
    }
}
