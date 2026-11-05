package io.github.saanliu.student.repository;

import io.github.saanliu.student.exception.StudentStorageException;
import io.github.saanliu.student.model.Student;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileStudentRepository extends AbstractFileStudentRepository {
    private static final CSVFormat CSV_FORMAT = CSVFormat.RFC4180.builder()
            .setRecordSeparator("\n")
            .setIgnoreEmptyLines(false)
            .get();

    public FileStudentRepository(Path file) {
        super(file);
        loadStudents();
    }

    @Override
    protected List<Student> readStudents() {
        List<Student> loaded = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // 兼容部分编辑器写入的 UTF-8 BOM，它不属于学生 id。
            reader.mark(1);
            if (reader.read() != '\uFEFF') {
                reader.reset();
            }
            try (CSVParser parser = CSV_FORMAT.parse(reader)) {
                for (CSVRecord record : parser.getRecords()) {
                    if (record.size() != 4) {
                        throw new StudentStorageException("Invalid CSV record " + record.getRecordNumber()
                                + " in " + file + ": expected 4 fields (id,name,className,score).");
                    }
                    double score;
                    try {
                        score = Double.parseDouble(record.get(3).strip());
                    } catch (NumberFormatException exception) {
                        throw new StudentStorageException("Invalid score in CSV record "
                                + record.getRecordNumber() + " in " + file + ".", exception);
                    }
                    // 保留全部记录，重复 id 和成绩范围等业务校验由 service 执行。
                    loaded.add(new Student(record.get(0), record.get(1), record.get(2), score));
                }
            }
            return loaded;
        } catch (NoSuchFileException exception) {
            // 首次运行没有数据文件时，从空数据开始，不在读取阶段创建文件。
            return List.of();
        } catch (IOException | UncheckedIOException exception) {
            throw new StudentStorageException("Could not read student data from " + file
                    + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    protected void writeStudents(List<Student> updated) {
        Path temporaryFile = null;
        try {
            Files.createDirectories(file.getParent());
            // 临时文件与正式文件放在同一目录，确保原子替换位于同一文件系统。
            temporaryFile = Files.createTempFile(file.getParent(), "students-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
                for (Student student : updated) {
                    printer.printRecord(student.id(), student.name(), student.className(),
                            Double.toString(student.score()));
                }
            }
            // 文件完整写入并关闭后再替换；不支持原子替换时让操作明确失败。
            Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new StudentStorageException("Could not save student data to " + file
                    + ". No in-memory changes were applied: " + exception.getMessage(), exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    // 清理失败不掩盖原始保存异常，遗留临时文件也不会被启动读取。
                }
            }
        }
    }
}
