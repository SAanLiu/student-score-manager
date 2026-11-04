package io.github.saanliu.student.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

// passRate 使用百分数，例如 75.0 表示及格率为 75%。
public record ScoreStatistics(long studentCount, double averageScore, double highestScore,
                              double lowestScore, double passRate, Map<String, Double> classAverages) {
    public ScoreStatistics {
        // 复制并保护内部 Map，同时保持班级名称的排序。
        classAverages = Collections.unmodifiableMap(new TreeMap<>(classAverages));
    }
}
